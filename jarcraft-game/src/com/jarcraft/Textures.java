package com.jarcraft;

import java.util.Random;

/** Procedurally generated 16x16 texture atlas (16 tiles in a row). */
public final class Textures {
    public static final int TILE = 16;
    public static final int TILES = 16;
    public static final int ATLAS_W = TILE * TILES; // 256
    public static final int ATLAS_H = TILE;

    /** ARGB pixels of the atlas, length ATLAS_W*ATLAS_H. */
    public static final int[] PIX = new int[ATLAS_W * ATLAS_H];

    static {
        generate();
    }

    private Textures() {}

    private static void tile(int t, Random r, int baseR, int baseG, int baseB, int amp) {
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int n = r.nextInt(amp * 2 + 1) - amp;
                PIX[t * TILE + y * ATLAS_W + x] =
                        rgb(clamp8(baseR + n), clamp8(baseG + n), clamp8(baseB + n));
            }
        }
    }

    private static void put(int t, int x, int y, int rr, int gg, int bb) {
        PIX[t * TILE + y * ATLAS_W + x] = rgb(clamp8(rr), clamp8(gg), clamp8(bb));
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static void generate() {
        Random r = new Random(90210);

        // 0 grass top
        tile(0, r, 106, 170, 64, 18);
        // 2 dirt
        tile(2, r, 134, 96, 67, 16);
        // 1 grass side: dirt with grass fringe on top
        tile(1, r, 134, 96, 67, 16);
        for (int x = 0; x < TILE; x++) {
            int fringe = 2 + r.nextInt(3);
            for (int y = 0; y < fringe; y++) {
                int n = r.nextInt(25) - 12;
                put(1, x, y, 106 + n, 170 + n, 64 + n);
            }
        }
        // 3 stone
        tile(3, r, 127, 127, 127, 14);
        // 4 cobble: stones with dark seams
        tile(4, r, 122, 122, 122, 12);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                boolean seam = ((x * 5 + y * 3) % 11 < 2) || (y % 5 == 0 && (x + y) % 7 < 3);
                if (seam) put(4, x, y, 78, 78, 82);
            }
        // 5 planks: boards with seams
        tile(5, r, 168, 132, 79, 10);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                if (y % 4 == 3) put(5, x, y, 112, 84, 48);
                else if ((y / 4) % 2 == 0 && x == 7) put(5, x, y, 120, 90, 52);
                else if ((y / 4) % 2 == 1 && x == 13) put(5, x, y, 120, 90, 52);
            }
        // 6 log side: vertical bark stripes
        tile(6, r, 104, 82, 50, 10);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                if ((x * 7 + (y / 4) * 3) % 9 < 2) put(6, x, y, 76, 58, 34);
            }
        // 7 log top: rings
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                int dx = x - 7, dy = y - 7;
                int d = (int) Math.sqrt(dx * dx + dy * dy);
                int n = r.nextInt(14) - 7;
                if (d >= 7) put(7, x, y, 104 + n, 82 + n, 50 + n);
                else {
                    boolean ring = d % 3 == 0;
                    put(7, x, y, ring ? 168 : 190, ring ? 132 : 152, ring ? 79 : 96);
                }
            }
        // 8 leaves: two-tone speckle
        tile(8, r, 64, 122, 42, 10);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                if (r.nextInt(3) == 0) put(8, x, y, 46, 96, 30);
                else if (r.nextInt(4) == 0) put(8, x, y, 88, 150, 56);
            }
        // 9 sand
        tile(9, r, 219, 207, 163, 12);
        // 10 water: wave bands
        tile(10, r, 52, 96, 201, 8);
        for (int y = 0; y < TILE; y++)
            for (int x = 0; x < TILE; x++) {
                if ((y + (x / 4)) % 8 < 2) put(10, x, y, 70, 118, 224);
            }
        // 11 bedrock
        tile(11, r, 70, 70, 74, 26);
        // 12-15 ores: stone base + specks
        int[][] ore = {
            { 40, 40, 44 },       // coal
            { 216, 175, 147 },    // iron
            { 252, 238, 75 },     // gold
            { 93, 236, 245 },     // diamond
        };
        for (int o = 0; o < 4; o++) {
            tile(12 + o, r, 127, 127, 127, 12);
            for (int k = 0; k < 9; k++) {
                int cx = r.nextInt(TILE - 2), cy = r.nextInt(TILE - 2);
                for (int d = 0; d < 3; d++) {
                    int x = cx + (d & 1), y = cy + (d >> 1);
                    put(12 + o, x, y, ore[o][0], ore[o][1], ore[o][2]);
                }
            }
        }
    }

    /** Sample one texel of tile t at integer texel coords (wrapped). */
    public static int texel(int t, int tx, int ty) {
        tx &= TILE - 1;
        ty &= TILE - 1;
        return PIX[t * TILE + ty * ATLAS_W + tx];
    }

    /** Shade an ARGB pixel by multiplying RGB with s (0..~2). */
    public static int shade(int argb, float s) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = (int) (r * s); g = (int) (g * s); b = (int) (b * s);
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
