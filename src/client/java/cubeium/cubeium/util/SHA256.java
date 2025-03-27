package cubeium.cubeium.util;

public class SHA256 {
    private static final int[] K = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
            0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
            0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
            0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    private static final int[] B = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };

    private static int rotr32(int x, int n) {
        return (x >>> n) | (x << (32 - n));
    }

    private static int bswap32(int x) {
        return ((x & 0xFF000000) >>> 24) |
                ((x & 0x00FF0000) >>> 8) |
                ((x & 0x0000FF00) << 8) |
                ((x & 0x000000FF) << 24);
    }

    public static long getVoronoiSHA(long seed) {
        int[] m = new int[64];
        m[0] = bswap32((int) (seed));
        m[1] = bswap32((int) (seed >>> 32));
        m[2] = 0x80000000;
        for (int i = 3; i < 15; i++) {
            m[i] = 0;
        }
        m[15] = 0x00000040;

        for (int i = 16; i < 64; i++) {
            m[i] = m[i - 7] + m[i - 16];
            int x = m[i - 15];
            m[i] += rotr32(x, 7) ^ rotr32(x, 18) ^ (x >>> 3);
            x = m[i - 2];
            m[i] += rotr32(x, 17) ^ rotr32(x, 19) ^ (x >>> 10);
        }

        int a0 = B[0], a1 = B[1], a2 = B[2], a3 = B[3];
        int a4 = B[4], a5 = B[5], a6 = B[6], a7 = B[7];

        for (int i = 0; i < 64; i++) {
            int x = a7 + K[i] + m[i];
            x += rotr32(a4, 6) ^ rotr32(a4, 11) ^ rotr32(a4, 25);
            x += (a4 & a5) ^ (~a4 & a6);

            int y = rotr32(a0, 2) ^ rotr32(a0, 13) ^ rotr32(a0, 22);
            y += (a0 & a1) ^ (a0 & a2) ^ (a1 & a2);

            a7 = a6;
            a6 = a5;
            a5 = a4;
            a4 = a3 + x;
            a3 = a2;
            a2 = a1;
            a1 = a0;
            a0 = x + y;
        }

        a0 += B[0];
        a1 += B[1];

        return ((long) bswap32(a0) << 32) | (bswap32(a1) & 0xFFFFFFFFL);
    }
}