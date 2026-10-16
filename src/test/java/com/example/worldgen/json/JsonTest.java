package com.example.worldgen.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void parsesAllValueKinds() {
        Object v = Json.parse(" {\"a\": [1, -2.5, 3e2, 0.0], \"b\": {\"c\": true, \"d\": false, \"e\": null},"
                + " \"s\": \"x\\\"y\\\\z\\n\\u00e9\\/\"} ");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(List.of(1L, -2.5, 300.0, 0.0), m.get("a"));
        Map<?, ?> b = (Map<?, ?>) m.get("b");
        assertEquals(Boolean.TRUE, b.get("c"));
        assertEquals(Boolean.FALSE, b.get("d"));
        assertSame(Json.NULL, b.get("e"));
        assertEquals("x\"y\\z\n\u00e9/", m.get("s"));
    }

    @Test
    void keepsDoublePrecisionOfVanillaValues() {
        Map<?, ?> m = (Map<?, ?>) Json.parse("{\"base_amplitude\": 0.8880832896205223, \"base_octave\": -9}");
        assertEquals(0.8880832896205223, m.get("base_amplitude"));
        assertEquals(-9L, m.get("base_octave"));
    }

    @Test
    void emptyContainers() {
        assertEquals(Map.of(), Json.parse("{}"));
        assertEquals(List.of(), Json.parse("[ ]"));
    }

    @Test
    void rejectsInvalidJson() {
        String[] bad = {"", "{", "[1,]", "{\"a\":1,}", "{'a':1}", "01", "1.", "-", "1e", "[1] 2", "tru", "nul",
                "{\"a\" 1}", "{\"a\":1 \"b\":2}", "\"unterminated", "\"bad \\x escape\"", "\"\u0001\"", "{\"a\":1,\"a\":2}",
                "// c\n{}", "NaN", "[\"\\u12\"]"};
        for (String s : bad) {
            assertThrows(Json.JsonParseException.class, () -> Json.parse(s), s);
        }
    }
}
