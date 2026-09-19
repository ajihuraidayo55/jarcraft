package com.jarcraft;

/** Classic Perlin noise (2D/3D) with fBm helpers. Java 8, no deps. */
public final class Noise {
    private final int[] perm = new int[512];

    public Noise(long seed) {
        java.util.Random r = new java.util.Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float grad2(int h, float x, float y) {
        switch (h & 7) {
            case 0: return  x + y;
            case 1: return -x + y;
            case 2: return  x - y;
            case 3: return -x - y;
            case 4: return  x;
            case 5: return -x;
            case 6: return  y;
            default: return -y;
        }
    }

    private static float grad3(int h, float x, float y, float z) {
        switch (h & 15) {
            case 0: return  x + y;
            case 1: return -x + y;
            case 2: return  x - y;
            case 3: return -x - y;
            case 4: return  x + z;
            case 5: return -x + z;
            case 6: return  x - z;
            case 7: return -x - z;
            case 8: return  y + z;
            case 9: return -y + z;
            case 10: return  y - z;
            case 11: return -y - z;
            case 12: return  x + y;
            case 13: return -y + z;
            case 14: return -x + y;
            default: return -y - z;
        }
    }

    public float noise2(float x, float y) {
        int xi = floorInt(x), yi = floorInt(y);
        float xf = x - xi, yf = y - yi;
        float u = fade(xf), v = fade(yf);
        int aa = perm[perm[xi & 255] + (yi & 255)];
        int ab = perm[perm[xi & 255] + ((yi + 1) & 255)];
        int ba = perm[perm[(xi + 1) & 255] + (yi & 255)];
        int bb = perm[perm[(xi + 1) & 255] + ((yi + 1) & 255)];
        float x1 = lerp(grad2(aa, xf, yf),     grad2(ba, xf - 1, yf),     u);
        float x2 = lerp(grad2(ab, xf, yf - 1), grad2(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v); // ~[-1,1]
    }

    public float noise3(float x, float y, float z) {
        int xi = floorInt(x), yi = floorInt(y), zi = floorInt(z);
        float xf = x - xi, yf = y - yi, zf = z - zi;
        float u = fade(xf), v = fade(yf), w = fade(zf);
        int A = perm[xi & 255], B = perm[(xi + 1) & 255];
        int AA = perm[A + (yi & 255)], AB = perm[A + ((yi + 1) & 255)];
        int BA = perm[B + (yi & 255)], BB = perm[B + ((yi + 1) & 255)];
        float x1 = lerp(grad3(perm[AA + (zi & 255)],         xf, yf, zf),
                         grad3(perm[BA + (zi & 255)],         xf - 1, yf, zf), u);
        float x2 = lerp(grad3(perm[AB + (zi & 255)],         xf, yf - 1, zf),
                         grad3(perm[BB + (zi & 255)],         xf - 1, yf - 1, zf), u);
        float y1 = lerp(x1, x2, v);
        float x3 = lerp(grad3(perm[AA + ((zi + 1) & 255)],   xf, yf, zf - 1),
                         grad3(perm[BA + ((zi + 1) & 255)],   xf - 1, yf, zf - 1), u);
        float x4 = lerp(grad3(perm[AB + ((zi + 1) & 255)],   xf, yf - 1, zf - 1),
                         grad3(perm[BB + ((zi + 1) & 255)],   xf - 1, yf - 1, zf - 1), u);
        float y2 = lerp(x3, x4, v);
        return lerp(y1, y2, w);
    }

    public float fbm2(float x, float y, int octaves) {
        float sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise2(x, y) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2.03f; y *= 2.03f;
            x += 17.7f; y += 9.1f;
        }
        return sum / norm;
    }

    public float fbm3(float x, float y, float z, int octaves) {
        float sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise3(x, y, z) * amp;
            norm += amp;
            amp *= 0.5f;
            x *= 2.05f; y *= 2.05f; z *= 2.05f;
            x += 11.3f; y += 27.9f; z += 5.2f;
        }
        return sum / norm;
    }

    private static int floorInt(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
