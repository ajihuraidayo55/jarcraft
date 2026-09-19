package com.jarcraft;

/** Fixed-size voxel world: 256 x 64 x 256, one byte per cell. */
public final class World {
    public static final int SX = 256, SY = 64, SZ = 256;
    private static final int AREA = SX * SZ;

    private final byte[] data = new byte[SX * SY * SZ];

    /** Cached surface height per column: first AIR y above the top solid. */
    private final int[] surf = new int[SX * SZ];

    public byte get(int x, int y, int z) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ || y >= SY) return Blocks.AIR;
        if (y < 0) return Blocks.BEDROCK; // world floor, prevents void falls
        return data[x + z * SX + y * AREA];
    }

    public void set(int x, int y, int z, byte id) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ || y < 0 || y >= SY) return;
        data[x + z * SX + y * AREA] = id;
    }

    public boolean solid(int x, int y, int z) {
        return Blocks.SOLID[get(x, y, z) & 0xFF];
    }

    /** Highest non-air y at column (x,z), or 0 if empty column. */
    public int highest(int x, int z) {
        for (int y = SY - 1; y >= 0; y--) {
            if (get(x, y, z) != Blocks.AIR) return y;
        }
        return 0;
    }

    /** Highest solid (walkable) y at column (x,z). */
    public int highestSolid(int x, int z) {
        for (int y = SY - 1; y >= 0; y--) {
            if (solid(x, y, z)) return y;
        }
        return 0;
    }

    public int count(byte id) {
        int n = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == id) n++;
        }
        return n;
    }

    // ---------------- surface cache (renderer ground pass) ----------------

    /** First AIR y above the highest solid block of column (x,z). */
    public int surfaceY(int x, int z) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ) return 0;
        return surf[x + z * SX];
    }

    /** Recompute the whole surface cache (after generation). */
    public void recomputeSurfaces() {
        for (int z = 0; z < SZ; z++)
            for (int x = 0; x < SX; x++)
                updateSurface(x, z);
    }

    /** Recompute one column after a block change. */
    public void updateSurface(int x, int z) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ) return;
        int y = SY - 1;
        while (y >= 0 && !solid(x, y, z)) y--;
        surf[x + z * SX] = y + 1;
    }
}
