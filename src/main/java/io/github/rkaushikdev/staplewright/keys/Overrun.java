package io.github.rkaushikdev.staplewright.keys;

/**
 * A symbol with more keys out than it is allowed.
 *
 * <p>An overrun is not proof of anything. It is a question worth asking, which is more than a stamp
 * ever produced.
 *
 * @param symbol the symbol, as the shop writes it
 * @param out how many keys cut to it are out
 * @param ceiling how many are allowed
 * @param authorised whether the ceiling is one somebody signed for rather than policy
 */
public record Overrun(String symbol, int out, int ceiling, boolean authorised) {

    /** How many keys over the ceiling the symbol is. */
    public int overBy() {
        return out - ceiling;
    }

    /** One line: {@code AA1: 6 out against policy ceiling 4}. */
    public String describe() {
        String signed = authorised ? "authorised" : "policy";
        return symbol + ": " + out + " out against " + signed + " ceiling " + ceiling;
    }
}
