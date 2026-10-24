#!/usr/bin/env python3
"""A tiny fake adbd (the TV side of ADB over TCP) for testing Lehko's ADB client without a TV.

It speaks the legacy (non-TLS) protocol: CNXN, AUTH (token / signature / public key), OPEN "shell:", WRTE, OKAY,
CLSE. Unknown keys are "approved by the user" after a short delay, like pressing OK on the TV's dialog. Shell
commands get canned answers from ANSWERS. Only the Python standard library is needed.

    python3 tools/fake_adbd.py [port]
"""
import base64
import os
import socket
import struct
import sys
import threading
import time

CNXN, AUTH, OPEN, OKAY, CLSE, WRTE = 0x4e584e43, 0x48545541, 0x4e45504f, 0x59414b4f, 0x45534c43, 0x45545257
SHA1_PREFIX = bytes([0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14])

ANSWERS = {
    "echo hello": "hello\n",
    "cmd role get-role-holders --user 0 android.app.role.HOME": "com.google.android.apps.tv.launcherx\n",
    "pm list packages -d": "package:com.example.off\n",
}
LOG = []
# Extra canned answers from a JSON file (command -> output), for script tests: FAKE_ADBD_ANSWERS=answers.json
if os.environ.get("FAKE_ADBD_ANSWERS"):
    import json
    ANSWERS.update(json.load(open(os.environ["FAKE_ADBD_ANSWERS"])))


# ---------------------------------------------------------------------------------------------------------------
# A simulated Google TV for HomeMode tests (python3 fake_adbd.py PORT --tv). It keeps state across connections and
# behaves like Android where it matters: turning off an app deletes the Wi-Fi networks it saved, a disabled home
# app stops being a HOME candidate, the HOME role falls back to another candidate, settings and files persist, a
# settings value goes through the shell's quoting.
# Test-only commands: "fake:reset", "fake:flag NAME 1|0", "fake:networks owner1,owner2", "fake:state" (JSON).
# ---------------------------------------------------------------------------------------------------------------
import json
import re

LAUNCHERX = "com.google.android.apps.tv.launcherx"
SETUP = "com.google.android.tungsten.setupwraith"
LEHKO = "ua.lehko.tv"


class TV:
    def __init__(self):
        self.lock = threading.Lock()
        self.reset()

    def reset(self):
        self.pkgs = {p: True for p in [
            LAUNCHERX, SETUP, LEHKO, "com.google.android.apps.tv.dreamx", "com.google.android.katniss",
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.android.tv.settings",
            "com.google.android.gms", "com.android.vending", "com.google.android.play.games"]}
        self.homes = {
            LAUNCHERX: LAUNCHERX + "/.home.HomeActivity",
            SETUP: SETUP + "/.RecoveryActivity",
            LEHKO: LEHKO + "/.HomeActivity",
            "com.android.tv.settings": "com.android.tv.settings/.FallbackHome",
        }
        self.holder = LAUNCHERX
        # No accessibility service on (as on Eugene's TV: enabled_accessibility_services null, accessibility_enabled 0).
        self.settings = {"secure": {"screensaver_components": "com.google.android.apps.tv.dreamx/.service.Backdrop",
                                    "screensaver_enabled": "1", "accessibility_enabled": "0"}, "global": {}}
        self.networks = [("HomeWiFi", "com.android.tv.settings")]
        self.running = {LAUNCHERX: 245678, "com.google.android.youtube.tv": 120000}
        self.kills = 0
        self.files = {}
        self.flags = {}
        self.log = []

    def candidates(self):
        return [c for p, c in self.homes.items() if self.pkgs.get(p)]

    def fallback_holder(self, exclude=None):
        for p, c in self.homes.items():
            if self.pkgs.get(p) and p != exclude and "FallbackHome" not in c:
                return p
        return ""

    def run(self, cmd):
        with self.lock:
            self.log.append(cmd)
            if "; " in cmd and cmd.startswith("echo "):
                return "".join(self.run_one(part) for part in cmd.split("; "))
            return self.run_one(cmd)

    def run_one(self, cmd):
        m = re.fullmatch(r"echo '(.*)' (>>?) (\S+)", cmd)
        if m:
            text, op, path = m.groups()
            self.files[path] = (self.files.get(path, "") if op == ">>" else "") + text + "\n"
            return ""
        if cmd == "fake:reset":
            self.reset()
            return "ok\n"
        if cmd.startswith("fake:flag "):
            _, name, value = cmd.split(" ")
            self.flags[name] = value == "1"
            return "ok\n"
        if cmd.startswith("fake:networks "):
            owners = cmd.split(" ", 1)[1]
            self.networks = [("Net%d" % i, o) for i, o in enumerate(owners.split(",")) if o]
            return "ok\n"
        if cmd == "fake:state":
            return json.dumps({"pkgs": self.pkgs, "holder": self.holder, "settings": self.settings,
                               "networks": self.networks, "files": self.files, "running": self.running,
                               "kills": self.kills}) + "\n"
        m = re.fullmatch(r"cat (\S+)( 2>/dev/null)?", cmd)
        if m:
            return self.files.get(m.group(1), "")
        m = re.fullmatch(r"rm -f (\S+)", cmd)
        if m:
            self.files.pop(m.group(1), None)
            return ""
        m = re.fullmatch(r"pm list packages ?(-d)?", cmd)
        if m:
            if self.flags.get("pm_broken"):
                return "Error: could not access the Package Manager.  Is the system running?\n"
            only_off = m.group(1) == "-d"
            return "".join("package:%s\n" % p for p, on in self.pkgs.items() if (not on) == only_off or not only_off)
        m = re.fullmatch(r"pm (disable-user --user 0|enable) (\S+)", cmd)
        if m:
            action, p = m.groups()
            if p not in self.pkgs:
                return "Error: java.lang.IllegalArgumentException: Unknown package: %s\n" % p
            if self.flags.get("pm_disable_fails") and action != "enable":
                return "Error: java.lang.SecurityException: Shell cannot change component state\n"
            if self.flags.get("setup_enable_fails") and action == "enable" and p == SETUP:
                return "Error: java.lang.SecurityException: Shell cannot change component state\n"
            on = action == "enable"
            self.pkgs[p] = on
            if not on:
                # Android 11+: networks saved by a turned-off app are removed.
                self.networks = [n for n in self.networks if n[1] != p]
                if self.holder == p:
                    self.holder = self.fallback_holder(exclude=p)
            return "Package %s new state: %s\n" % (p, "enabled" if on else "disabled-user")
        m = re.fullmatch(r"pm path (\S+)", cmd)
        if m:
            return "package:/data/app/%s/base.apk\n" % m.group(1) if m.group(1) in self.pkgs else ""
        m = re.fullmatch(r"pm uninstall (\S+)", cmd)
        if m:
            p = m.group(1)
            if p in self.pkgs:
                del self.pkgs[p]
                if self.holder == p:
                    self.holder = self.fallback_holder(exclude=p)
                return "Success\n"
            return "Failure [DELETE_FAILED_INTERNAL_ERROR]\n"
        if cmd == "cmd role get-role-holders --user 0 android.app.role.HOME":
            return self.holder + "\n" if self.holder else "\n"
        m = re.fullmatch(r"cmd role (add|remove)-role-holder --user 0 android\.app\.role\.HOME (\S+)", cmd)
        if m:
            op, p = m.groups()
            if op == "add" and self.pkgs.get(p) and p in self.homes and not (self.flags.get("home_refused") and p == LEHKO):
                self.holder = p
            elif op == "remove" and self.holder == p:
                self.holder = self.fallback_holder(exclude=p)
            return ""
        if cmd.startswith("cmd package resolve-activity --brief") and "category.HOME" in cmd:
            if self.holder:
                return "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n%s\n" % self.homes[self.holder]
            return "priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false\nandroid/com.android.internal.app.ResolverActivity\n"
        if cmd.startswith("cmd package query-activities --brief") and "category.HOME" in cmd:
            c = self.candidates()
            out = "%d activities found:\n" % len(c)
            for i, comp in enumerate(c):
                out += "  Activity #%d:\n    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n    %s\n" % (i, comp)
            return out
        m = re.fullmatch(r"cmd package set-home-activity --user 0 (\S+)", cmd)
        if m:
            comp = m.group(1)
            p = comp.split("/")[0]
            if self.pkgs.get(p) and self.homes.get(p) == comp and not (self.flags.get("home_refused") and p == LEHKO):
                self.holder = p
                return "Success\n"
            return "Error: Failed to set the home activity\n"
        m = re.fullmatch(r"settings (get|put|delete) (secure|global) (\S+) ?(\S+)?", cmd)
        if m:
            op, ns, key, value = m.groups()
            if op == "get":
                return self.settings[ns].get(key, "null") + "\n"
            if op == "put":
                # As the TV's shell does: single quotes keep a value as it is, without them $NAME expands (to nothing).
                quoted = re.fullmatch(r"'([^']*)'", value or "")
                self.settings[ns][key] = quoted.group(1) if quoted else re.sub(r"\$\w+", "", value or "")
            else:
                self.settings[ns].pop(key, None)
            return ""
        if cmd.startswith("dumpsys wifi | grep"):
            if self.flags.get("dumpsys_broken"):
                return ""
            return "WifiConfigManager\n" + "".join("cname=%s\n" % o for _, o in self.networks)
        if cmd == "cmd wifi list-networks 2>&1":
            if self.flags.get("list_unsupported"):
                return "Unknown command: list-networks\n"
            if not self.networks:
                return "No networks\n"
            return "Network Id      SSID                         Security type\n" + "".join(
                "%d            %s                      wpa2-psk\n" % (i, n[0]) for i, n in enumerate(self.networks))
        m = re.fullmatch(r"dumpsys meminfo (\S+)", cmd)
        if m:
            p = m.group(1)
            if not self.pkgs.get(p) or p not in self.running:
                return "No process found for: %s\n" % p
            pss = self.running[p]
            return MEMINFO.format(pid=4242, pkg=p, pss=pss, native=pss // 5, rss=pss + 60000)
        if cmd == "am kill-all":
            # Only cached apps go: the home screen and what is on screen stay.
            self.kills += 1
            self.running = {p: v for p, v in self.running.items() if p == self.holder}
            return ""
        if cmd.startswith("am start"):
            return "Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.HOME] }\n"
        return "/system/bin/sh: %s: inaccessible or not found\n" % cmd.split(" ")[0]


# What "dumpsys meminfo PKG" prints on Android 12-14 (shortened).
MEMINFO = """Applications Memory Usage (in Kilobytes):
Uptime: 3456789 Realtime: 3456789

** MEMINFO in pid {pid} [{pkg}] **
                   Pss  Private  Private  SwapPss      Rss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty    Total     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------   ------
  Native Heap    {native}    {native}        0      120    {native}    40960    30000    10960
        TOTAL   {pss}   200000    10000     5000   {rss}    70000    50000    20000

 App Summary
                       Pss(KB)                        Rss(KB)
                        ------                         ------
           Java Heap:    12345                          23456
               TOTAL PSS:   {pss}            TOTAL RSS:   {rss}       TOTAL SWAP PSS:     5000
"""

TV_MODE = "--tv" in sys.argv
THE_TV = TV()


def parse_pubkey(blob: bytes):
    raw = base64.b64decode(blob.split(b" ")[0].rstrip(b"\0"))
    words = struct.unpack_from("<I", raw, 0)[0]
    n = int.from_bytes(raw[8:8 + words * 4], "little")
    e = struct.unpack_from("<I", raw, 8 + words * 8)[0]
    return n, e


def verify(n, e, token, sig):
    k = (n.bit_length() + 7) // 8
    if len(sig) != k:
        return False
    em = pow(int.from_bytes(sig, "big"), e, n).to_bytes(k, "big")
    t = SHA1_PREFIX + token
    expected = b"\x00\x01" + b"\xff" * (k - 3 - len(t)) + b"\x00" + t
    return em == expected


class Conn:
    def __init__(self, sock, authorized):
        self.s = sock
        self.authorized = authorized
        self.next_id = 100

    def send(self, cmd, a0, a1, data=b""):
        hdr = struct.pack("<6I", cmd, a0, a1, len(data), sum(data) & 0xffffffff, cmd ^ 0xffffffff)
        self.s.sendall(hdr + data)

    def read(self):
        hdr = self._exact(24)
        cmd, a0, a1, ln, chk, magic = struct.unpack("<6I", hdr)
        assert magic == cmd ^ 0xffffffff, "bad magic"
        data = self._exact(ln) if ln else b""
        assert sum(data) & 0xffffffff == chk or chk == 0, "bad checksum"
        return cmd, a0, a1, data

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise EOFError
            buf += chunk
        return buf

    def run(self):
        cmd, a0, a1, data = self.read()
        assert cmd == CNXN and data.startswith(b"host::"), "expected CNXN"
        token = os.urandom(20)
        self.send(AUTH, 1, 0, token)
        while True:
            cmd, a0, a1, data = self.read()
            assert cmd == AUTH, "expected AUTH"
            if a0 == 2:  # signature
                if any(verify(n, e, token, data) for n, e in self.authorized):
                    LOG.append("auth: signature ok")
                    break
                LOG.append("auth: unknown key")
                token = os.urandom(20)
                self.send(AUTH, 1, 0, token)
            elif a0 == 3:  # public key: the "user" presses OK after a moment
                self.authorized.append(parse_pubkey(data))
                LOG.append("auth: key approved (" + data.split(b" ", 1)[1].rstrip(b"\0").decode() + ")")
                time.sleep(0.5)
                break
        self.send(CNXN, 0x01000001, 256 * 1024, b"device::ro.product.name=fake;ro.product.model=FakeTV;features=cmd\0")
        while True:
            try:
                cmd, a0, a1, data = self.read()
            except EOFError:
                return
            if cmd == OPEN:
                local = a0
                remote = self.next_id
                self.next_id += 1
                service = data.rstrip(b"\0").decode()
                assert service.startswith("shell:"), service
                command = service[6:]
                LOG.append("shell: " + command)
                self.send(OKAY, remote, local)
                out = THE_TV.run(command) if TV_MODE else ANSWERS.get(command, "")
                if command.startswith("big"):
                    out = "x" * 100000 + "\n"
                for i in range(0, len(out.encode()), 4096):
                    self.send(WRTE, remote, local, out.encode()[i:i + 4096])
                    c2, b0, b1, _ = self.read()
                    assert c2 == OKAY and b1 == remote, "host must OKAY each WRTE"
                self.send(CLSE, remote, local)
            elif cmd == CLSE:
                pass
            elif cmd == OKAY:
                pass


def serve(port):
    authorized = []
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(4)
    print("fake adbd on", port, flush=True)
    while True:
        c, _ = srv.accept()

        def handle(c=c):
            try:
                Conn(c, authorized).run()
            except EOFError:
                pass
            except Exception as e:  # noqa: BLE001
                LOG.append("ERROR: %r" % e)
                print("ERROR", repr(e), flush=True)
            finally:
                c.close()
                print("\n".join(LOG), flush=True)
                LOG.clear()

        threading.Thread(target=handle, daemon=True).start()


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    serve(int(args[0]) if args else 5555)
