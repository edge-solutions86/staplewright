package io.github.rkaushikdev.staplewright.rekey;

/**
 * One way of doing a rekey, by the name the memo gives it, with what it costs.
 *
 * @param name what the option is called: {@code swap cores}
 * @param estimate what it costs
 */
public record PricedOption(String name, Estimate estimate) {}
