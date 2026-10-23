package com.example.worldgen.feature;

import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@code HolderSet<Block>} as {@code RegistryCodecs.holderSet(Registries.BLOCK)} decodes it: a {@code "#namespace:tag"}
 * string (a tag), a single block id string, or a list of block ids (a direct set, in list order). {@link #contains} is
 * {@code state.is(holderSet)}.
 */
public final class BlockSet {

    private final String tag;
    private final List<String> blocks;
    private final BlockTags tags;

    private BlockSet(String tag, List<String> blocks, BlockTags tags) {
        this.tag = tag;
        this.blocks = blocks;
        this.tags = tags;
    }

    public static BlockSet parse(Object json, BlockTags tags) {
        if (json instanceof String s) {
            if (s.startsWith("#")) return new BlockSet(normalize(s.substring(1)), null, tags);
            return new BlockSet(null, List.of(block(s)), tags);
        }
        List<String> ids = new ArrayList<>();
        for (Object o : CodecJson.list(json, "block holder set")) {
            if (!(o instanceof String s) || s.startsWith("#")) throw new CodecJson.CodecException("Not a block id: " + o);
            ids.add(block(s));
        }
        return new BlockSet(null, List.copyOf(ids), tags);
    }

    /** A block id of the set (vanilla checks it against the full block registry; the ids come from the vanilla data). */
    private static String block(String id) {
        return normalize(id);
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** {@code state.is(holderSet)}. */
    public boolean contains(StateRef state) {
        if (tag != null) return tags.is(state, tag);
        return blocks.contains(state.id());
    }

    /**
     * {@code HolderSet.getRandomElement(random)} of a direct set: {@code Util.getRandomSafe(contents, random)} (empty for an
     * empty list, else {@code contents.get(random.nextInt(size))}). Tag sets are not used where this is called.
     */
    public Optional<String> getRandomElement(RandomSource random) {
        if (tag != null) throw new UnsupportedOperationException("random element of a tag set");
        return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(random.nextInt(blocks.size())));
    }

    @Override
    public String toString() {
        return tag != null ? "#" + tag : blocks.toString();
    }
}
