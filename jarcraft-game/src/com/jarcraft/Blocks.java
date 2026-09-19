package com.jarcraft;

/** Block ids, names, physical flags and texture mapping. */
public final class Blocks {
    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte COBBLE = 4;
    public static final byte PLANKS = 5;
    public static final byte LOG = 6;
    public static final byte LEAVES = 7;
    public static final byte SAND = 8;
    public static final byte WATER = 9;
    public static final byte BEDROCK = 10;
    public static final byte COAL_ORE = 11;
    public static final byte IRON_ORE = 12;
    public static final byte GOLD_ORE = 13;
    public static final byte DIAMOND_ORE = 14;

    public static final int COUNT = 15;

    public static final String[] NAME = {
        "Air", "Grass Block", "Dirt", "Stone", "Cobblestone", "Oak Planks",
        "Oak Log", "Leaves", "Sand", "Water", "Bedrock",
        "Coal Ore", "Iron Ore", "Gold Ore", "Diamond Ore"
    };

    /** Solid for physics/collision (water and air are not solid). */
    public static final boolean[] SOLID = {
        false, true, true, true, true, true, true, true, true, false, true,
        true, true, true, true
    };

    /** Blocks the player may break (bedrock is unbreakable). */
    public static boolean breakable(byte b) {
        return b != AIR && b != BEDROCK && b != WATER;
    }

    // Texture atlas tile indices (must match Textures layout)
    public static final int T_GRASS_TOP = 0, T_GRASS_SIDE = 1, T_DIRT = 2,
            T_STONE = 3, T_COBBLE = 4, T_PLANKS = 5, T_LOG_SIDE = 6,
            T_LOG_TOP = 7, T_LEAVES = 8, T_SAND = 9, T_WATER = 10,
            T_BEDROCK = 11, T_COAL = 12, T_IRON = 13, T_GOLD = 14,
            T_DIAMOND = 15;

    /** Tile id for the top / side / bottom face of each block. */
    public static final int[][] TILES = {
        /* AIR          */ { 0, 0, 0 },
        /* GRASS        */ { T_GRASS_TOP, T_GRASS_SIDE, T_DIRT },
        /* DIRT         */ { T_DIRT, T_DIRT, T_DIRT },
        /* STONE        */ { T_STONE, T_STONE, T_STONE },
        /* COBBLE       */ { T_COBBLE, T_COBBLE, T_COBBLE },
        /* PLANKS       */ { T_PLANKS, T_PLANKS, T_PLANKS },
        /* LOG          */ { T_LOG_TOP, T_LOG_SIDE, T_LOG_TOP },
        /* LEAVES       */ { T_LEAVES, T_LEAVES, T_LEAVES },
        /* SAND         */ { T_SAND, T_SAND, T_SAND },
        /* WATER        */ { T_WATER, T_WATER, T_WATER },
        /* BEDROCK      */ { T_BEDROCK, T_BEDROCK, T_BEDROCK },
        /* COAL_ORE     */ { T_COAL, T_COAL, T_COAL },
        /* IRON_ORE     */ { T_IRON, T_IRON, T_IRON },
        /* GOLD_ORE     */ { T_GOLD, T_GOLD, T_GOLD },
        /* DIAMOND_ORE  */ { T_DIAMOND, T_DIAMOND, T_DIAMOND },
    };

    /** Hotbar palette shown in the HUD (1-9). */
    public static final byte[] PALETTE = {
        GRASS, DIRT, STONE, COBBLE, PLANKS, LOG, LEAVES, SAND, COAL_ORE
    };

    private Blocks() {}
}
