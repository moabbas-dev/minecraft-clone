package com.example.worldgen.biome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON reader (RFC 8259) used by {@link BiomeDefinitionLoader}. Objects become
 * insertion-ordered {@code Map<String, Object>}, arrays {@code List<Object>}, strings {@code String},
 * {@code true/false} {@code Boolean}, {@code null} {@link #NULL}, and numbers {@link Num} keeping the
 * literal text so a float is parsed straight from it with {@link Float#parseFloat} (as Gson's
 * {@code LazilyParsedNumber.floatValue()} does for vanilla's {@code Codec.FLOAT}).
 * It exists because no shared project JSON reader was present when this package was written.
 */
public final class BiomeJson {
    /** The JSON {@code null} literal. */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    /** A JSON number kept as its literal text. */
    public record Num(String text) {
        public float floatValue() {
            return Float.parseFloat(this.text);
        }

        public double doubleValue() {
            return Double.parseDouble(this.text);
        }

        @Override
        public String toString() {
            return this.text;
        }
    }

    private final String s;
    private int pos;

    private BiomeJson(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        BiomeJson parser = new BiomeJson(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("trailing data");
        }
        return value;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON " + message + " at offset " + this.pos);
    }

    private void skipWhitespace() {
        while (this.pos < this.s.length()) {
            char c = this.s.charAt(this.pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                this.pos++;
            } else {
                break;
            }
        }
    }

    private Object readValue() {
        if (this.pos >= this.s.length()) {
            throw this.error("unexpected end");
        }
        char c = this.s.charAt(this.pos);
        switch (c) {
            case '{':
                return this.readObject();
            case '[':
                return this.readArray();
            case '"':
                return this.readString();
            case 't':
                this.expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                this.expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                this.expectLiteral("null");
                return NULL;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return this.readNumber();
                }
                throw this.error("unexpected character '" + c + "'");
        }
    }

    private void expectLiteral(String literal) {
        if (!this.s.startsWith(literal, this.pos)) {
            throw this.error("expected " + literal);
        }
        this.pos += literal.length();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        this.pos++;
        this.skipWhitespace();
        if (this.peek() == '}') {
            this.pos++;
            return Collections.unmodifiableMap(map);
        }
        while (true) {
            this.skipWhitespace();
            if (this.peek() != '"') {
                throw this.error("expected object key");
            }
            String key = this.readString();
            this.skipWhitespace();
            if (this.peek() != ':') {
                throw this.error("expected ':'");
            }
            this.pos++;
            this.skipWhitespace();
            if (map.put(key, this.readValue()) != null) {
                throw this.error("duplicate key " + key);
            }
            this.skipWhitespace();
            char c = this.peek();
            this.pos++;
            if (c == '}') {
                return Collections.unmodifiableMap(map);
            }
            if (c != ',') {
                throw this.error("expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        this.pos++;
        this.skipWhitespace();
        if (this.peek() == ']') {
            this.pos++;
            return Collections.unmodifiableList(list);
        }
        while (true) {
            this.skipWhitespace();
            list.add(this.readValue());
            this.skipWhitespace();
            char c = this.peek();
            this.pos++;
            if (c == ']') {
                return Collections.unmodifiableList(list);
            }
            if (c != ',') {
                throw this.error("expected ',' or ']'");
            }
        }
    }

    private char peek() {
        if (this.pos >= this.s.length()) {
            throw this.error("unexpected end");
        }
        return this.s.charAt(this.pos);
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        this.pos++;
        while (true) {
            char c = this.peek();
            this.pos++;
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw this.error("control character in string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = this.peek();
            this.pos++;
            switch (e) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (this.pos + 4 > this.s.length()) {
                        throw this.error("bad unicode escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(this.s.substring(this.pos, this.pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw this.error("bad unicode escape");
                    }
                    this.pos += 4;
                    break;
                default:
                    throw this.error("bad escape");
            }
        }
    }

    private Num readNumber() {
        int start = this.pos;
        if (this.peek() == '-') {
            this.pos++;
        }
        if (this.peek() == '0') {
            this.pos++;
        } else {
            this.digits();
        }
        if (this.pos < this.s.length() && this.s.charAt(this.pos) == '.') {
            this.pos++;
            this.digits();
        }
        if (this.pos < this.s.length() && (this.s.charAt(this.pos) == 'e' || this.s.charAt(this.pos) == 'E')) {
            this.pos++;
            if (this.pos < this.s.length() && (this.s.charAt(this.pos) == '+' || this.s.charAt(this.pos) == '-')) {
                this.pos++;
            }
            this.digits();
        }
        return new Num(this.s.substring(start, this.pos));
    }

    private void digits() {
        int start = this.pos;
        while (this.pos < this.s.length() && Character.isDigit(this.s.charAt(this.pos))
            && this.s.charAt(this.pos) <= '9') {
            this.pos++;
        }
        if (this.pos == start) {
            throw this.error("expected digit");
        }
    }
}
