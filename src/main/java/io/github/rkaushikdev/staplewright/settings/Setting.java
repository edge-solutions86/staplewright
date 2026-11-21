package io.github.rkaushikdev.staplewright.settings;

/**
 * One knob: the group it lives in, the field it sets, and the variable it is read from.
 *
 * @param group the group of {@link ShopSettings} the field sits in, blank for a field of the
 *     settings themselves
 * @param attribute the field, written the way the variable names it ({@code max_run})
 * @param variable the variable's name without the prefix
 */
public record Setting(String group, String attribute, String variable) {}
