package ua.lehko.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Random;

public class AdbTest {
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05,
            0x00, 0x04, 0x14};

    private static KeyPair key() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    /** Our hand-rolled PKCS#1 v1.5 signature must equal the JDK's over the same DigestInfo bytes. */
    @Test public void signatureMatchesJdk() throws Exception {
        KeyPair kp = key();
        Random r = new Random(42);
        for (int i = 0; i < 20; i++) {
            byte[] token = new byte[20];
            r.nextBytes(token);
            Signature jdk = Signature.getInstance("NONEwithRSA");
            jdk.initSign(kp.getPrivate());
            jdk.update(SHA1_PREFIX);
            jdk.update(token);
            assertArrayEquals(jdk.sign(), Adb.sign((RSAPrivateKey) kp.getPrivate(), token));
        }
    }

    /** adbd's key format: words, n0inv, n (LE words), rr = 2^4096 mod n, e; base64 + " name\0". */
    @Test public void publicKeyBlobFormat() throws Exception {
        KeyPair kp = key();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        String blob = new String(Adb.publicKeyBlob(pub), StandardCharsets.US_ASCII);
        assertTrue(blob.endsWith("\0"));
        String b64 = blob.substring(0, blob.indexOf(' '));
        assertTrue(blob.substring(blob.indexOf(' ')).startsWith(" Lehko@"));
        ByteBuffer b = ByteBuffer.wrap(Base64.getDecoder().decode(b64)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(524, b.remaining());
        assertEquals(64, b.getInt());
        long n0inv = b.getInt() & 0xffffffffL;
        BigInteger n = BigInteger.ZERO, rr = BigInteger.ZERO;
        for (int i = 0; i < 64; i++) n = n.or(BigInteger.valueOf(b.getInt() & 0xffffffffL).shiftLeft(32 * i));
        for (int i = 0; i < 64; i++) rr = rr.or(BigInteger.valueOf(b.getInt() & 0xffffffffL).shiftLeft(32 * i));
        assertEquals(pub.getModulus(), n);
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), rr);
        assertEquals(65537, b.getInt());
        // n0inv * n[0] == -1 (mod 2^32)
        long n0 = n.longValue() & 0xffffffffL;
        assertEquals(0xffffffffL, (n0inv * n0) & 0xffffffffL);
    }

    @Test public void checksum() {
        assertEquals(0, Adb.checksum(null));
        assertEquals(255 + 1, Adb.checksum(new byte[]{(byte) 0xff, 1}));
    }
}
