package ua.lehko.tv;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * A minimal ADB client that talks to the TV's own debugging port (127.0.0.1:5555). This is how Lehko can make itself
 * the home screen and turn the stock launcher off without a computer: those commands need the "shell" user.
 * <p>
 * The first time, the TV asks "Allow USB debugging?" for Lehko's key; tick "Always allow" and press OK. The key never
 * leaves the app's private storage. Only fixed commands from {@link HomeMode} and {@link CleanupActivity} are sent,
 * never anything from the network.
 * <p>
 * Protocol: https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt
 */
final class Adb implements Closeable {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257, STLS = 0x534c5453;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int KEY_BITS = 2048;
    /** DER prefix of a SHA-1 DigestInfo: adbd signs the 20-byte token as if it were a SHA-1 digest. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05,
            0x00, 0x04, 0x14};

    static final int PORT = 5555;

    /** Reasons a connection can fail, so the screen can say what to do. */
    static final class Failure extends IOException {
        static final int PORT_CLOSED = 1, NOT_ALLOWED = 2, TLS_ONLY = 3, PROTOCOL = 4;
        final int reason;

        Failure(int reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /** Called (on the worker thread) when the TV shows the "Allow USB debugging?" window. */
    interface Approval {
        void waiting();
    }

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocalId = 1;

    private Adb(Socket socket) throws IOException {
        this.socket = socket;
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
    }

    /**
     * Connects and authenticates; if the TV doesn't know Lehko's key yet, waits up to {@code approvalMs} for you.
     * {@code approvalMs <= 0}: never ask, fail with NOT_ALLOWED instead (for background checks).
     */
    static Adb connect(Context c, int approvalMs, Approval approval) throws IOException {
        return connect(c.getNoBackupFilesDir(), "127.0.0.1", PORT, approvalMs, approval);
    }

    /** The same, with the key folder and address given (tests talk to a fake TV). */
    static Adb connect(File keyDir, String host, int port, int approvalMs, Approval approval) throws IOException {
        KeyPair keys = keys(keyDir);
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), 3000);
        } catch (IOException e) {
            s.close();
            throw new Failure(Failure.PORT_CLOSED, "debugging port is closed");
        }
        Adb adb;
        try {
            s.setSoTimeout(10_000);
            s.setTcpNoDelay(true);
            adb = new Adb(s);
        } catch (IOException | RuntimeException e) {
            s.close();
            throw e;
        }
        try {
            adb.send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.US_ASCII));
            boolean signed = false, offered = false;
            while (true) {
                Message m;
                try {
                    m = adb.read();
                } catch (SocketTimeoutException e) {
                    throw offered ? new Failure(Failure.NOT_ALLOWED, "debugging was not allowed on the TV")
                            : new Failure(Failure.PROTOCOL, "the TV's debugging service didn't answer");
                } catch (java.io.EOFException e) {
                    // adbd may simply hang up when "Deny" is pressed.
                    throw offered ? new Failure(Failure.NOT_ALLOWED, "debugging was not allowed on the TV")
                            : new Failure(Failure.PROTOCOL, "the TV's debugging service closed the connection");
                }
                if (m.command == CNXN) {
                    s.setSoTimeout(30_000);
                    return adb;
                }
                if (m.command == STLS) throw new Failure(Failure.TLS_ONLY, "the TV only offers wireless debugging (TLS)");
                if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw new Failure(Failure.PROTOCOL, "unexpected reply");
                if (!signed) {
                    adb.send(AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
                    signed = true;
                } else if (approvalMs <= 0) {
                    // Background checks never pop up "Allow USB debugging?" out of the blue.
                    throw new Failure(Failure.NOT_ALLOWED, "the TV doesn't know Lehko's key any more");
                } else if (!offered) {
                    // The TV doesn't know the key: offer it, the TV shows "Allow USB debugging?".
                    adb.send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob((RSAPublicKey) keys.getPublic()));
                    offered = true;
                    if (approval != null) approval.waiting();
                    s.setSoTimeout(approvalMs);
                } else {
                    throw new Failure(Failure.NOT_ALLOWED, "the TV rejected Lehko's key");
                }
            }
        } catch (IOException | RuntimeException e) {
            adb.close();
            throw e;
        }
    }

    /** Runs a command in the TV's shell and returns what it printed (stdout and stderr together). */
    String shell(String command) throws IOException {
        int local = nextLocalId++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int remote = 0;
        while (true) {
            Message m = read();
            if (m.arg1 != local) continue;
            if (m.command == OKAY) {
                remote = m.arg0;
            } else if (m.command == WRTE) {
                remote = m.arg0;
                if (m.data != null) output.write(m.data);
                send(OKAY, local, remote, null);
            } else if (m.command == CLSE) {
                if (remote != 0) send(CLSE, local, remote, null);
                break;
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    // MARK: - Wire format: 24-byte little-endian header, then the payload

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(len).putInt(checksum(data)).putInt(~command);
        if (data != null) b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Message read() throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt(); // checksum: not checked (TCP already does)
        int magic = b.getInt();
        if (magic != ~m.command) throw new Failure(Failure.PROTOCOL, "bad message");
        if (len < 0 || len > 1024 * 1024) throw new Failure(Failure.PROTOCOL, "message too large");
        if (len > 0) {
            m.data = new byte[len];
            in.readFully(m.data);
        }
        return m;
    }

    static int checksum(byte[] data) {
        int sum = 0;
        if (data != null) for (byte x : data) sum += x & 0xff;
        return sum;
    }

    // MARK: - Keys

    /** Lehko's RSA key pair, created once and kept in the app's private storage. */
    private static synchronized KeyPair keys(File dir) throws IOException {
        File priv = new File(dir, "adbkey"), pub = new File(dir, "adbkey.pub");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.isFile() && pub.isFile()) {
                return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                        kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(KEY_BITS);
            KeyPair kp = g.generateKeyPair();
            writeAll(pub, kp.getPublic().getEncoded());
            writeAll(priv, kp.getPrivate().getEncoded());
            return kp;
        } catch (GeneralSecurityException e) {
            throw new IOException("key: " + e.getMessage());
        }
    }

    /** PKCS#1 v1.5 signature of the token (as a SHA-1 DigestInfo), done with plain RSA math: no provider quirks. */
    static byte[] sign(RSAPrivateKey key, byte[] token) {
        BigInteger n = key.getModulus();
        int k = (n.bitLength() + 7) / 8;
        byte[] em = new byte[k];
        int t = SHA1_PREFIX.length + token.length;
        em[0] = 0;
        em[1] = 1;
        for (int i = 2; i < k - t - 1; i++) em[i] = (byte) 0xff;
        em[k - t - 1] = 0;
        System.arraycopy(SHA1_PREFIX, 0, em, k - t, SHA1_PREFIX.length);
        System.arraycopy(token, 0, em, k - token.length, token.length);
        byte[] s = new BigInteger(1, em).modPow(key.getPrivateExponent(), n).toByteArray();
        byte[] out = new byte[k];
        // BigInteger may add a sign byte or drop leading zeros: right-align into k bytes.
        int copy = Math.min(s.length, k);
        System.arraycopy(s, s.length - copy, out, k - copy, copy);
        return out;
    }

    /** The public key in adbd's format (mincrypt RSAPublicKey, base64) plus a name shown in the TV's list. */
    static byte[] publicKeyBlob(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        int words = KEY_BITS / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(32 * i).intValue());
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(32 * i).intValue());
        b.putInt(key.getPublicExponent().intValue());
        String model = Build.MODEL == null ? "tv" : Build.MODEL;
        String name = " Lehko@" + model.replaceAll("[^A-Za-z0-9._-]", "_");
        String blob = java.util.Base64.getEncoder().encodeToString(b.array()) + name + "\0";
        return blob.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new IOException("can't save the key");
    }
}
