package com.example.inventory;

import com.example.world.BlockType;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Crafting recipes. Shaped only, position-independent: a recipe matches wherever it sits in the
 * grid as long as its bounding box does (so a single log crafts from any cell, and a 2x2 recipe
 * works in the 3x3 table too). Every ingredient and result is a block, because this project's
 * items are all {@link BlockType}s - tools and other items need the item model generalized
 * first (see {@link ItemStack}).
 *
 * Patterns are rows of characters; '.' means "must be empty".
 */
public final class Recipes {
    private Recipes() {
    }

    private record Recipe(String[] rows, Map<Character, Predicate<BlockType>> legend, ItemStack result) {
        int height() {
            return rows.length;
        }

        int width() {
            return rows[0].length();
        }

        boolean matches(BlockType[][] cells) {
            for (int y = 0; y < height(); y++) {
                for (int x = 0; x < width(); x++) {
                    char c = rows[y].charAt(x);
                    BlockType cell = cells[y][x];
                    if (c == '.') {
                        if (cell != null) return false;
                    } else if (cell == null || !legend.get(c).test(cell)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    static boolean isLog(BlockType t) {
        return t == BlockType.OAK_LOG || t == BlockType.BIRCH_LOG || t == BlockType.SPRUCE_LOG
                || t == BlockType.JUNGLE_LOG || t == BlockType.ACACIA_LOG || t == BlockType.MANGROVE_LOG
                || t == BlockType.DARK_OAK_LOG || t == BlockType.CHERRY_LOG;
    }

    private static Predicate<BlockType> only(BlockType b) {
        return t -> t == b;
    }

    /** Wood-handled tools take planks for the head; stone tools take cobblestone; iron tools
     *  take an iron ingot (obtainable now that furnace smelting exists - see
     *  com.example.cooking). Axe recipes are mirrored (both a left- and right-leaning head)
     *  since Recipe.matches is exact-shape, not mirror-aware, and real vanilla accepts both;
     *  pickaxe and shovel are already symmetric so need only one shape each.
     */
    private static Recipe tool(String[] rows, BlockType headMaterial, ItemStack result) {
        return new Recipe(rows, Map.of('H', only(headMaterial), 'S', only(BlockType.STICK)), result);
    }

    private static final List<Recipe> ALL = List.of(
            // Any log species -> oak planks (the only planks block that exists yet).
            new Recipe(new String[]{"L"}, Map.of('L', Recipes::isLog), new ItemStack(BlockType.OAK_PLANKS, 4)),
            new Recipe(new String[]{"PP", "PP"}, Map.of('P', only(BlockType.OAK_PLANKS)), new ItemStack(BlockType.CRAFTING_TABLE, 1)),
            new Recipe(new String[]{"SS", "SS"}, Map.of('S', only(BlockType.SAND)), new ItemStack(BlockType.SANDSTONE, 1)),
            new Recipe(new String[]{"CCC", "C.C", "CCC"}, Map.of('C', only(BlockType.COBBLESTONE)), new ItemStack(BlockType.FURNACE, 1)),
            new Recipe(new String[]{"PPP", "P.P", "PPP"}, Map.of('P', only(BlockType.OAK_PLANKS)), new ItemStack(BlockType.CHEST, 1)),
            new Recipe(new String[]{"P", "P"}, Map.of('P', only(BlockType.OAK_PLANKS)), new ItemStack(BlockType.STICK, 4)),

            tool(new String[]{"HHH", ".S.", ".S."}, BlockType.OAK_PLANKS, new ItemStack(BlockType.WOODEN_PICKAXE, 1)),
            tool(new String[]{"HHH", ".S.", ".S."}, BlockType.COBBLESTONE, new ItemStack(BlockType.STONE_PICKAXE, 1)),
            tool(new String[]{"HH", "HS", ".S"}, BlockType.OAK_PLANKS, new ItemStack(BlockType.WOODEN_AXE, 1)),
            tool(new String[]{"HH", "SH", "S."}, BlockType.OAK_PLANKS, new ItemStack(BlockType.WOODEN_AXE, 1)),
            tool(new String[]{"HH", "HS", ".S"}, BlockType.COBBLESTONE, new ItemStack(BlockType.STONE_AXE, 1)),
            tool(new String[]{"HH", "SH", "S."}, BlockType.COBBLESTONE, new ItemStack(BlockType.STONE_AXE, 1)),
            tool(new String[]{"H", "S", "S"}, BlockType.OAK_PLANKS, new ItemStack(BlockType.WOODEN_SHOVEL, 1)),
            tool(new String[]{"H", "S", "S"}, BlockType.COBBLESTONE, new ItemStack(BlockType.STONE_SHOVEL, 1)),
            tool(new String[]{"HHH", ".S.", ".S."}, BlockType.IRON_INGOT, new ItemStack(BlockType.IRON_PICKAXE, 1)),
            tool(new String[]{"HH", "HS", ".S"}, BlockType.IRON_INGOT, new ItemStack(BlockType.IRON_AXE, 1)),
            tool(new String[]{"HH", "SH", "S."}, BlockType.IRON_INGOT, new ItemStack(BlockType.IRON_AXE, 1)),
            tool(new String[]{"H", "S", "S"}, BlockType.IRON_INGOT, new ItemStack(BlockType.IRON_SHOVEL, 1)),

            // Blast Furnace: 3 iron ingots on top, iron-furnace-iron in the middle, 3 smooth
            // stone on the bottom - the request's own exact vanilla shape.
            new Recipe(new String[]{"III", "IFI", "SSS"},
                    Map.of('I', only(BlockType.IRON_INGOT), 'F', only(BlockType.FURNACE), 'S', only(BlockType.SMOOTH_STONE)),
                    new ItemStack(BlockType.BLAST_FURNACE, 1)),
            // Smoker: a log above/below/beside the furnace on every cardinal side, any species -
            // the request's own exact vanilla shape, generalized to every log this project has.
            new Recipe(new String[]{".L.", "LFL", ".L."},
                    Map.of('L', Recipes::isLog, 'F', only(BlockType.FURNACE)),
                    new ItemStack(BlockType.SMOKER, 1))
    );

    /** Number of registered recipes (for tests / debug output). */
    public static int count() {
        return ALL.size();
    }

    /**
     * What the given grid crafts, or {@link ItemStack#EMPTY}. {@code cells} is row-major,
     * {@code width * height} long, with null for an empty cell.
     */
    public static ItemStack craft(BlockType[] cells, int width, int height) {
        if (cells.length != width * height) throw new IllegalArgumentException("grid size mismatch");
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y * width + x] == null) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) return ItemStack.EMPTY;

        int w = maxX - minX + 1, h = maxY - minY + 1;
        BlockType[][] trimmed = new BlockType[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) trimmed[y][x] = cells[(minY + y) * width + (minX + x)];

        for (Recipe r : ALL) {
            if (r.width() == w && r.height() == h && r.matches(trimmed)) return r.result();
        }
        return ItemStack.EMPTY;
    }
}
