package cn.myfrank.stationbuilder.scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency-free JSON reader/writer used by the MTR schedule planner.
 *
 * <p>The planner deliberately does not depend on Gson (or on any Minecraft / MTR class) so the
 * whole {@code scheduler} package stays unit-testable outside the game, and so the same source can
 * be shared by every loader and Minecraft version without remapping.</p>
 *
 * <p>Parsing produces {@link Map}, {@link List}, {@link String}, {@link Long} (integral numbers),
 * {@link Double} (fractional numbers), {@link Boolean} and {@code null}. The reader is strict
 * about syntax and reports the character offset of a failure.</p>
 */
public final class Json {

    private Json() {
    }

    /** Thrown when a document is not syntactically valid JSON. */
    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }

    public static Object parse(String text) throws ParseException {
        if (text == null) {
            throw new ParseException("JSON input is null");
        }
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Typed accessors. These are intentionally forgiving: an unexpected type
    // in a third-party API response must never crash the planner.
    // ------------------------------------------------------------------

    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    public static Object first(Object value, String... keys) {
        Map<String, Object> map = object(value);
        for (String key : keys) {
            Object found = map.get(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static String string(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return fallback;
    }

    public static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                try {
                    return (long) Double.parseDouble(text.trim());
                } catch (NumberFormatException ignoredAgain) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    public static int intValue(Object value, int fallback) {
        long result = longValue(value, fallback);
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    public static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Writer
    // ------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value, 0, true);
        return out.toString();
    }

    public static String writeCompact(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value, 0, false);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int depth, boolean pretty) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            writeMap(out, map, depth, pretty);
        } else if (value instanceof Iterable<?> iterable) {
            writeIterable(out, iterable, depth, pretty);
        } else if (value instanceof Object[] array) {
            writeIterable(out, java.util.Arrays.asList(array), depth, pretty);
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map, int depth, boolean pretty) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1, pretty);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(pretty ? ": " : ":");
            writeValue(out, entry.getValue(), depth + 1, pretty);
        }
        newline(out, depth, pretty);
        out.append('}');
    }

    private static void writeIterable(StringBuilder out, Iterable<?> iterable, int depth, boolean pretty) {
        List<Object> values = new ArrayList<>();
        for (Object value : iterable) {
            values.add(value);
        }
        if (values.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            newline(out, depth + 1, pretty);
            writeValue(out, values.get(i), depth + 1, pretty);
        }
        newline(out, depth, pretty);
        out.append(']');
    }

    private static void newline(StringBuilder out, int depth, boolean pretty) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ------------------------------------------------------------------
    // Parser
    // ------------------------------------------------------------------

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        ParseException error(String message) {
            return new ParseException(message + " at offset " + index);
        }

        void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw error("expected " + literal);
            }
            index += literal.length();
            return value;
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++; // '{'
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') {
                    throw error("expected object key");
                }
                String key = parseString();
                skipWhitespace();
                if (atEnd() || text.charAt(index) != ':') {
                    throw error("expected ':'");
                }
                index++;
                map.put(key, parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated object");
                }
                char c = text.charAt(index);
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == '}') {
                    index++;
                    return map;
                }
                throw error("expected ',' or '}'");
            }
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            index++; // '['
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated array");
                }
                char c = text.charAt(index);
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == ']') {
                    index++;
                    return list;
                }
                throw error("expected ',' or ']'");
            }
        }

        private String parseString() {
            index++; // opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("unterminated escape");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw error("truncated unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        index += 4;
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw error("invalid unicode escape '\\u" + hex + "'");
                        }
                    }
                    default -> throw error("invalid escape '\\" + escape + "'");
                }
            }
        }

        private Object parseNumber() {
            int start = index;
            if (!atEnd() && (text.charAt(index) == '-' || text.charAt(index) == '+')) {
                index++;
            }
            boolean fractional = false;
            while (!atEnd()) {
                char c = text.charAt(index);
                if (Character.isDigit(c)) {
                    index++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fractional = fractional || c == '.' || c == 'e' || c == 'E';
                    index++;
                } else {
                    break;
                }
            }
            String token = text.substring(start, index);
            if (token.isEmpty()) {
                index = start;
                throw error("expected a value");
            }
            try {
                if (fractional) {
                    return Double.parseDouble(token);
                }
                try {
                    return Long.parseLong(token);
                } catch (NumberFormatException overflow) {
                    return Double.parseDouble(token);
                }
            } catch (NumberFormatException e) {
                index = start;
                throw error("invalid number '" + token + "'");
            }
        }
    }
}
