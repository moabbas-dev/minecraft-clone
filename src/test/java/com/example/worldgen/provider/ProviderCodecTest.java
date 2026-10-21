package com.example.worldgen.provider;

import com.example.worldgen.json.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Decoding, bounds and validation rules read from the 26.3 codec lambdas and {@code minInclusive/maxInclusive} bodies. */
class ProviderCodecTest {

    private static Object json(String s) {
        return Json.parseWithNumberLiterals(s);
    }

    @Test
    void intProviderBounds() {
        IntProvider clamped = IntProvider.parse(json("{\"type\":\"clamped\",\"source\":{\"type\":\"uniform\",\"min_inclusive\":-10,\"max_inclusive\":3},\"min_inclusive\":-2,\"max_inclusive\":5}"));
        assertEquals(-2, clamped.minInclusive());
        assertEquals(3, clamped.maxInclusive());
        IntProvider weighted = IntProvider.parse(json("{\"type\":\"weighted_list\",\"distribution\":[{\"data\":4,\"weight\":1},{\"data\":{\"type\":\"uniform\",\"min_inclusive\":-1,\"max_inclusive\":9},\"weight\":0}]}"));
        assertEquals(-1, weighted.minInclusive());
        assertEquals(9, weighted.maxInclusive());
        assertSame(IntProvider.ConstantInt.ZERO, IntProvider.parse(json("0")));
        assertInstanceOf(IntProvider.TrapezoidInt.class, IntProvider.TrapezoidInt.triangle(3));
    }

    @Test
    void intProviderValidation() {
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parse(json("{\"type\":\"uniform\",\"min_inclusive\":3,\"max_inclusive\":2}")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parse(json("{\"type\":\"trapezoid\",\"min\":0,\"max\":4,\"plateau\":5}")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parse(json("{\"type\":\"weighted_list\",\"distribution\":[{\"data\":4,\"weight\":0}]}")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parse(json("{\"type\":\"weighted_list\",\"distribution\":[{\"data\":4,\"weight\":-1}]}")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parseNonNegative(json("{\"type\":\"uniform\",\"min_inclusive\":-1,\"max_inclusive\":2}")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parsePositive(json("0")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parse(json("{\"type\":\"other:uniform\",\"min_inclusive\":1,\"max_inclusive\":2}")));
        assertThrows(CodecJson.CodecException.class, () -> IntProvider.parse(json("{\"type\":\"nope\"}")));
    }

    @Test
    void floatProviderValidation() {
        FloatProvider u = FloatProvider.parse(json("{\"type\":\"uniform\",\"min_inclusive\":-1.0,\"max_exclusive\":-0.4}"), -1.0F, 1.0F);
        assertEquals(-1.0F, u.min());
        assertEquals(-0.4F, u.max());
        assertThrows(CodecJson.CodecException.class, () -> FloatProvider.parse(json("{\"type\":\"uniform\",\"min_inclusive\":1.0,\"max_exclusive\":1.0}")));
        assertThrows(CodecJson.CodecException.class, () -> FloatProvider.parse(json("{\"type\":\"trapezoid\",\"min\":0.0,\"max\":1.0,\"plateau\":2.0}")));
        assertThrows(CodecJson.CodecException.class, () -> FloatProvider.parse(json("{\"type\":\"clamped_normal\",\"mean\":0,\"deviation\":1,\"min\":2.0,\"max\":1.0}")));
        assertThrows(CodecJson.CodecException.class, () -> FloatProvider.parse(json("-0.1"), 0.0F));
        assertThrows(CodecJson.CodecException.class, () -> FloatProvider.parse(json("1.5"), -1.0F, 1.0F));
        assertSame(FloatProvider.ConstantFloat.ZERO, FloatProvider.parse(json("0.0")));
        assertThrows(IllegalArgumentException.class, () -> FloatProvider.UniformFloat.of(1.0F, 1.0F));
    }

    @Test
    void verticalAnchorsResolveAgainstTheContext() {
        WorldGenerationContext ctx = new WorldGenerationContext(-64, 384, 63);
        assertEquals(-56, VerticalAnchor.parse(json("{\"above_bottom\":8}")).resolveY(ctx));
        assertEquals(309, VerticalAnchor.parse(json("{\"below_top\":10}")).resolveY(ctx));
        assertEquals(180, VerticalAnchor.parse(json("{\"absolute\":180}")).resolveY(ctx));
        assertEquals(58, VerticalAnchor.parse(json("{\"relative_to_sea_level\":-5}")).resolveY(ctx));
        assertEquals(-64, VerticalAnchor.BOTTOM.resolveY(ctx));
        assertEquals(319, VerticalAnchor.TOP.resolveY(ctx));
        // xor: two alternatives decoding is an error; out-of-range (intRange MIN_Y..MAX_Y) does not decode
        assertThrows(CodecJson.CodecException.class, () -> VerticalAnchor.parse(json("{\"absolute\":1,\"above_bottom\":2}")));
        assertThrows(CodecJson.CodecException.class, () -> VerticalAnchor.parse(json("{\"absolute\":5000}")));
        assertThrows(CodecJson.CodecException.class, () -> VerticalAnchor.parse(json("{}")));
    }

    @Test
    void worldGenerationContextFromGenerator() {
        WorldGenerationContext ctx = WorldGenerationContext.forGenerator(0, 256, 0, 128, 32);
        assertEquals(0, ctx.getMinGenY());
        assertEquals(128, ctx.getGenDepth());
        assertEquals(32, ctx.seaLevel());
    }

    @Test
    void heightProviderDecoding() {
        assertInstanceOf(HeightProvider.ConstantHeight.class, HeightProvider.parse(json("{\"absolute\":3}")));
        HeightProvider.TrapezoidHeight t = (HeightProvider.TrapezoidHeight) HeightProvider.parse(json(
                "{\"type\":\"trapezoid\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":10}}"));
        assertEquals(0, t.plateau());
        HeightProvider.BiasedToBottomHeight b = (HeightProvider.BiasedToBottomHeight) HeightProvider.parse(json(
                "{\"type\":\"biased_to_bottom\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":10}}"));
        assertEquals(1, b.inner());
        assertThrows(CodecJson.CodecException.class, () -> HeightProvider.parse(json(
                "{\"type\":\"biased_to_bottom\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":10},\"inner\":0}")));
    }
}
