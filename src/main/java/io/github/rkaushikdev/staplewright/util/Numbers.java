package io.github.rkaushikdev.staplewright.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Numbers written the way the shop's pages print them.
 *
 * <p>Fixed places round the exact binary value half to even, so {@code 0.125} prints {@code
 * 0.12} and {@code 2.675} prints {@code 2.67} (it is stored a hair under). A negative value that
 * rounds to nothing keeps its sign. A double written whole is its shortest exact form: plain
 * between one ten thousandth and ten to the sixteenth, in exponent form outside that.
 */
public final class Numbers {

    private Numbers() {}

    /** {@code value} to {@code places} decimal places. */
    public static String fixed(double value, int places) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        String text =
                new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).toPlainString();
        boolean negative = value < 0 || (value == 0 && 1 / value < 0);
        if (negative && !text.startsWith("-")) {
            return "-" + text;
        }
        return text;
    }

    /** {@code value} rounded to {@code places} decimal places, a tie going to the even digit. */
    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** {@code value} to the nearest whole number, a tie going to the even one. */
    public static long round(double value) {
        return (long) Math.rint(value);
    }

    /** A whole number with a comma between each group of three digits. */
    public static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** A double in its shortest exact form, which is how a page prints one written whole. */
    public static String repr(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0.0" : "0.0";
        }
        BigDecimal exact = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = exact.unscaledValue().toString();
        int exponent = digits.length() - exact.scale() - 1;
        String sign = value < 0 ? "-" : "";
        if (exponent >= -4 && exponent < 16) {
            String plain = exact.toPlainString();
            return sign + (plain.contains(".") ? plain : plain + ".0");
        }
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        String power = String.format(Locale.ROOT, "%02d", Math.abs(exponent));
        return sign + mantissa + "e" + (exponent < 0 ? "-" : "+") + power;
    }
}
