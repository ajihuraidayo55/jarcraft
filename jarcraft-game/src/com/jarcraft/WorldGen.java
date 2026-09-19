package com.jarcraft;

import java.util.Random;

/** Terrain generation: heightmap, caves, ores, trees, water, beaches. */
public final class WorldGen {
    public static final int SEA = 30;

    private final Noise n1, n2, n3, cave, ore;
    private final Random treeRnd;

    public WorldGen(long seed) {
        n1 = new Noise(seed);
        n2 = new Noise(seed + 101);
        n3 = new Noise(seed + 202);
        cave = new Noise(seed + 303);
        ore = new Noise(seed + 404);
        treeRnd = new Random(seed + 505);
    }

    public void generate(World w) {
        int[] hmap = new int[World.SX * World.SZ];

        // --- terrain columns ---
        for (int z = 0; z < World.SZ; z++) {
            for (int x = 0; x < World.SX; x++) {
                float base = n1.fbm2(x / 130f, z / 130f, 4);          // continents
                float detail = n2.fbm2(x / 31f + 40f, z / 31f - 25f, 3); // hills
                float hh = 30 + base * 13f + detail * 6f;
                int h = Math.round(hh);
                if (h < 5) h = 5;
                if (h > World.SY - 14) h = World.SY - 14;
                hmap[x + z * World.SX] = h;

                w.set(x, 0, z, Blocks.BEDROCK);
                for (int y = 1; y <= h; y++) {
                    byte id;
                    if (y < h - 3) {
                        id = Blocks.STONE;
                    } else if (y < h) {
                        id = Blocks.DIRT;
                    } else { // surface block
                        if (h <= SEA + 1) {
                            id = Blocks.SAND;            // beach & lake floor
                        } else if (h >= 46) {
                            id = Blocks.STONE;           // rocky peaks
                        } else {
                            id = Blocks.GRASS;
                        }
                    }
                    w.set(x, y, z, id);
                }
                // water fill
                for (int y = h + 1; y <= SEA; y++) {
                    w.set(x, y, z, Blocks.WATER);
                }
            }
        }

        // --- caves (below the surface, away from water columns) ---
        for (int z = 1; z < World.SZ - 1; z++) {
            for (int x = 1; x < World.SX - 1; x++) {
                int h = hmap[x + z * World.SX];
                if (h <= SEA + 1) continue; // don't drain lakes through beaches
                for (int y = 4; y < h - 2; y++) {
                    float c = cave.fbm3(x / 22f, y / 13f, z / 22f, 3);
                    if (c > 0.54f) w.set(x, y, z, Blocks.AIR);
                }
            }
        }

        // --- ores in stone ---
        for (int z = 0; z < World.SZ; z++) {
            for (int x = 0; x < World.SX; x++) {
                int h = hmap[x + z * World.SX];
                for (int y = 2; y < h - 3; y++) {
                    if (w.get(x, y, z) != Blocks.STONE) continue;
                    float o = ore.noise3(x / 7f, y / 7f, z / 7f);
                    if (o < 0.62f) continue;
                    byte oreId;
                    if (y < 12) {
                        float pick = ore.noise3(x / 3f + 90f, y / 3f, z / 3f);
                        if (pick > 0.62f) oreId = Blocks.DIAMOND_ORE;
                        else if (pick > 0.30f) oreId = Blocks.GOLD_ORE;
                        else oreId = Blocks.IRON_ORE;
                    } else if (y < 24) {
                        float pick = ore.noise3(x / 3f + 90f, y / 3f, z / 3f);
                        oreId = pick > 0.45f ? Blocks.IRON_ORE : Blocks.COAL_ORE;
                    } else {
                        oreId = Blocks.COAL_ORE;
                    }
                    w.set(x, y, z, oreId);
                }
            }
        }

        // --- trees on grass ---
        for (int z = 3; z < World.SZ - 3; z++) {
            for (int x = 3; x < World.SX - 3; x++) {
                if (treeRnd.nextInt(160) != 0) continue;
                int h = hmap[x + z * World.SX];
                if (w.get(x, h, z) != Blocks.GRASS) continue;
                if (tooCloseToTree(w, x, z, h)) continue;
                placeTree(w, x, h + 1, z, 4 + treeRnd.nextInt(3));
            }
        }

        w.recomputeSurfaces();
    }

    /** Find a pleasant grass spawn near the world centre. */
    public static float[] findSpawn(World w) {
        int cx = World.SX / 2, cz = World.SZ / 2;
        for (int r = 0; r < 100; r += 2) {
            for (int a = 0; a < 8; a++) {
                int x = cx + (int) (Math.cos(a * Math.PI / 4) * r);
                int z = cz + (int) (Math.sin(a * Math.PI / 4) * r);
                if (x < 2 || x >= World.SX - 2 || z < 2 || z >= World.SZ - 2) continue;
                int h = w.highestSolid(x, z);
                if (h > SEA && w.get(x, h, z) == Blocks.GRASS) {
                    return new float[] { x + 0.5f, h + 1f, z + 0.5f };
                }
            }
        }
        int h = w.highestSolid(cx, cz);
        return new float[] { cx + 0.5f, h + 1f, cz + 0.5f };
    }

    private boolean tooCloseToTree(World w, int x, int z, int h) {
        for (int dz = -2; dz <= 2; dz++)
            for (int dx = -2; dx <= 2; dx++) {
                if (w.get(x + dx, h + 1, z + dz) == Blocks.LOG) return true;
            }
        return false;
    }

    private void placeTree(World w, int x, int baseY, int z, int th) {
        for (int i = 0; i < th; i++) {
            w.set(x, baseY + i, z, Blocks.LOG);
        }
        int top = baseY + th - 1;
        // canopy: two wide layers, then a small cap
        for (int dy = -2; dy <= 0; dy++) {
            int rad = dy <= -1 ? 2 : 1;
            for (int dz = -rad; dz <= rad; dz++)
                for (int dx = -rad; dx <= rad; dx++) {
                    if (dx == 0 && dz == 0 && dy <= 0 && baseY + th + dy < baseY + th) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && treeRnd.nextBoolean()) continue;
                    int lx = x + dx, ly = top + dy + 1, lz = z + dz;
                    if (w.get(lx, ly, lz) == Blocks.AIR) w.set(lx, ly, lz, Blocks.LEAVES);
                }
        }
        w.set(x, top + 2, z, Blocks.LEAVES);
        if (w.get(x + 1, top + 1, z) == Blocks.AIR) w.set(x + 1, top + 1, z, Blocks.LEAVES);
        if (w.get(x - 1, top + 1, z) == Blocks.AIR) w.set(x - 1, top + 1, z, Blocks.LEAVES);
        if (w.get(x, top + 1, z + 1) == Blocks.AIR) w.set(x, top + 1, z + 1, Blocks.LEAVES);
        if (w.get(x, top + 1, z - 1) == Blocks.AIR) w.set(x, top + 1, z - 1, Blocks.LEAVES);
    }
}
