package com.example.worldgen.carver;

import com.example.worldgen.json.Json;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.provider.FloatProvider;
import com.example.worldgen.provider.HeightProvider;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.provider.VerticalAnchor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every 26.3 carver JSON decodes to the expected record (values from the data files, defaults from the codecs). */
class CarverLoaderTest {

    private static final Path DATA = Path.of("data");

    private static void assumeData() {
        Assumptions.assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/carver")), "restored data/ needed");
    }

    @Test
    void cave() throws IOException {
        assumeData();
        CaveWorldCarver c = (CaveWorldCarver) CarverLoader.load(DATA, "cave");
        assertEquals(0.15F, c.probability());
        assertEquals(new HeightProvider.UniformHeight(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)), c.y());
        assertEquals(new IntProvider.VeryBiasedToBottomInt(0, 14), c.count());
        assertEquals(new FloatProvider.TrapezoidFloat(0.0F, 3.0F, 1.0F), c.thickness());
        assertTrue(c.weirdThicknessBias());
        assertEquals(new FloatProvider.UniformFloat(0.1F, 0.9F), c.roomVerticalRadiusMultiplier());
        assertEquals(new FloatProvider.UniformFloat(0.7F, 1.4F), c.horizontalRadiusMultiplier());
        assertEquals(new FloatProvider.UniformFloat(0.8F, 1.3F), c.verticalRadiusMultiplier());
        assertEquals(FloatProvider.ConstantFloat.of(1.0F), c.startVerticalRadiusMultiplier());
        assertEquals(new FloatProvider.UniformFloat(-1.0F, -0.4F), c.floorLevel());
    }

    @Test
    void caveExtraUnderground() throws IOException {
        assumeData();
        CaveWorldCarver c = (CaveWorldCarver) CarverLoader.load(DATA, "cave_extra_underground");
        assertEquals(0.07F, c.probability());
        assertEquals(new HeightProvider.UniformHeight(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(47)), c.y());
        assertEquals(new IntProvider.VeryBiasedToBottomInt(0, 14), c.count());
    }

    @Test
    void canyon() throws IOException {
        assumeData();
        CanyonWorldCarver c = (CanyonWorldCarver) CarverLoader.load(DATA, "canyon");
        assertEquals(0.01F, c.probability());
        assertEquals(new HeightProvider.UniformHeight(VerticalAnchor.absolute(10), VerticalAnchor.absolute(67)), c.y());
        assertEquals(new FloatProvider.UniformFloat(-0.125F, 0.125F), c.verticalRotation());
        assertEquals(new CanyonWorldCarver.Shape(new FloatProvider.UniformFloat(0.75F, 1.0F),
                new FloatProvider.TrapezoidFloat(0.0F, 6.0F, 2.0F), 3, new FloatProvider.UniformFloat(0.75F, 1.0F),
                1.0F, 0.0F, FloatProvider.ConstantFloat.of(3.0F)), c.shape());
    }

    @Test
    void netherCave() throws IOException {
        assumeData();
        CaveWorldCarver c = (CaveWorldCarver) CarverLoader.load(DATA, "nether_cave");
        assertEquals(0.2F, c.probability());
        assertEquals(new HeightProvider.UniformHeight(VerticalAnchor.absolute(0), VerticalAnchor.belowTop(1)), c.y());
        assertEquals(new IntProvider.VeryBiasedToBottomInt(0, 9), c.count());
        assertEquals(FloatProvider.ConstantFloat.of(5.0F), c.startVerticalRadiusMultiplier());
        assertEquals(FloatProvider.ConstantFloat.of(-0.7F), c.floorLevel());
        assertFalse(c.weirdThicknessBias());
    }

    @Test
    void codecRules() {
        String base = "\"y\":{\"absolute\":0},\"count\":1,\"thickness\":1.0,\"room_vertical_radius_multiplier\":1.0,"
                + "\"horizontal_radius_multiplier\":1.0,\"vertical_radius_multiplier\":1.0";
        assertInstanceOf(CaveWorldCarver.class, CarverLoader.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"cave\",\"probability\":1.0,\"floor_level\":-1.0," + base + "}")));
        // probability is floatRange(0, 1); floor_level is FloatProviders.codec(-1, 1); thickness codec(0); count non-negative
        assertThrows(CodecJson.CodecException.class, () -> CarverLoader.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"cave\",\"probability\":1.5,\"floor_level\":0.0," + base + "}")));
        assertThrows(CodecJson.CodecException.class, () -> CarverLoader.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"cave\",\"probability\":0.5,\"floor_level\":-1.5," + base + "}")));
        assertThrows(CodecJson.CodecException.class, () -> CarverLoader.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"cave\",\"probability\":0.5,\"floor_level\":0.0," + base.replace("\"count\":1", "\"count\":-1") + "}")));
        assertThrows(CodecJson.CodecException.class, () -> CarverLoader.parse(Json.parseWithNumberLiterals(
                "{\"type\":\"cave\",\"probability\":0.5,\"floor_level\":0.0," + base.replace("\"thickness\":1.0", "\"thickness\":-1.0") + "}")));
        assertThrows(CodecJson.CodecException.class, () -> CarverLoader.parse(Json.parseWithNumberLiterals("{\"type\":\"ravine\"}")));
    }
}
