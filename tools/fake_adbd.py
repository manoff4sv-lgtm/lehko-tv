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
        self.send(CNXN, 0x01000001, 256 * 1024, b"device::ro.product.name=fake;ro.product.model=FakeTV;features=shell_v2\0")
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
                out = ANSWERS.get(command, "")
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
    serve(int(sys.argv[1]) if len(sys.argv) > 1 else 5555)
