package com.example.worldgen.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict recursive-descent JSON (RFC 8259) reader for the vanilla data files. Values map
 * to: object {@code Map<String,Object>} (insertion ordered, duplicate keys rejected), array
 * {@code List<Object>}, string {@link String}, number {@link Double} (or {@link Long} for
 * integer literals that fit), {@link Boolean}, and {@code null} as {@link #NULL}. Anything that is
 * not valid JSON (trailing commas, comments, single quotes, leading zeros, bare control
 * characters, trailing content) is rejected with a {@link JsonParseException}.
 */
public final class Json {

    /** Marker for a JSON {@code null} so that map lookups can tell "absent" from "null". */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private final String text;
    private final boolean keepNumberLiterals;
    private int pos;

    private Json(String text, boolean keepNumberLiterals) {
        this.text = text;
        this.keepNumberLiterals = keepNumberLiterals;
    }

    public static Object parse(String text) {
        return parse(text, false);
    }

    /**
     * Like {@link #parse(String)} but every number becomes a {@link NumberLiteral} keeping its source text, so
     * that {@code float}/{@code int} reads convert the literal itself, as vanilla's JSON stack does (Gson's
     * {@code LazilyParsedNumber}: {@code floatValue = Float.parseFloat(text)}, {@code doubleValue = Double.parseDouble(text)}).
     */
    public static Object parseWithNumberLiterals(String text) {
        return parse(text, true);
    }

    private static Object parse(String text, boolean keepNumberLiterals) {
        Json parser = new Json(text, keepNumberLiterals);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("Trailing content");
        }
        return value;
    }

    private Object readValue() {
        if (pos >= text.length()) {
            throw error("Unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                expectWord("true");
                return Boolean.TRUE;
            case 'f':
                expectWord("false");
                return Boolean.FALSE;
            case 'n':
                expectWord("null");
                return NULL;
            default:
                if (c == '-' || c >= '0' && c <= '9') {
                    return readNumber();
                }
                throw error("Unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> readObject() {
        pos++;
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return Collections.unmodifiableMap(map);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a string key");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = readValue();
            if (map.containsKey(key)) {
                throw error("Duplicate key \"" + key + "\"");
            }
            map.put(key, value);
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return Collections.unmodifiableMap(map);
            }
            if (c != ',') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        pos++;
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return Collections.unmodifiableList(list);
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return Collections.unmodifiableList(list);
            }
            if (c != ',') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw error("Control character in string");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> sb.append(readHex4());
                default -> throw error("Invalid escape '\\" + e + "'");
            }
        }
    }

    private char readHex4() {
        if (pos + 4 > text.length()) {
            throw error("Truncated \\u escape");
        }
        int v = 0;
        for (int i = 0; i < 4; i++) {
            int d = Character.digit(text.charAt(pos + i), 16);
            if (d < 0) {
                throw error("Invalid \\u escape");
            }
            v = v << 4 | d;
        }
        pos += 4;
        return (char) v;
    }

    private Object readNumber() {
        int start = pos;
        boolean integral = true;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                pos++;
            }
        } else {
            throw error("Expected digit");
        }
        if (peek() == '.') {
            integral = false;
            pos++;
            if (!isDigit(peek())) {
                throw error("Expected digit after '.'");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            integral = false;
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw error("Expected exponent digit");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        String literal = text.substring(start, pos);
        if (keepNumberLiterals) {
            return new NumberLiteral(literal);
        }
        if (integral) {
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException tooLarge) {
                return Double.parseDouble(literal);
            }
        }
        return Double.parseDouble(literal);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void expectWord(String word) {
        if (!text.startsWith(word, pos)) {
            throw error("Expected '" + word + "'");
        }
        pos += word.length();
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("Expected '" + c + "'");
        }
    }

    /** The current character, or {@code '\0'} at end of input (never valid where a token is required). */
    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw error("Unexpected end of input");
        }
        return text.charAt(pos++);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private JsonParseException error(String message) {
        return new JsonParseException(message + " at offset " + pos);
    }

    /**
     * A JSON number kept as its source text. The conversions mirror Gson's {@code LazilyParsedNumber} (verified
     * with {@code javap} on gson 2.13): {@code floatValue} is {@code Float.parseFloat}, {@code doubleValue} is
     * {@code Double.parseDouble}, {@code longValue} is {@code Long.parseLong}, {@code intValue} tries
     * {@code Integer.parseInt}, then {@code (int) Long.parseLong}, then {@code BigDecimal.intValue()}.
     */
    public static final class NumberLiteral extends Number {

        private final String literal;

        public NumberLiteral(String literal) {
            this.literal = literal;
        }

        public String literal() {
            return literal;
        }

        @Override
        public int intValue() {
            try {
                return Integer.parseInt(literal);
            } catch (NumberFormatException e) {
                try {
                    return (int) Long.parseLong(literal);
                } catch (NumberFormatException e2) {
                    return new java.math.BigDecimal(literal).intValue();
                }
            }
        }

        @Override
        public long longValue() {
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException e) {
                return new java.math.BigDecimal(literal).longValue();
            }
        }

        @Override
        public float floatValue() {
            return Float.parseFloat(literal);
        }

        @Override
        public double doubleValue() {
            return Double.parseDouble(literal);
        }

        @Override
        public String toString() {
            return literal;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NumberLiteral other && literal.equals(other.literal);
        }

        @Override
        public int hashCode() {
            return literal.hashCode();
        }
    }

    /** Thrown for malformed JSON. */
    public static final class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }
}
