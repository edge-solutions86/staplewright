package io.github.rkaushikdev.staplewright.keys;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole code machine setup for one key: a line a chamber, bow first.
 *
 * @param family the chart family the key is cut against
 * @param bitting what the key is cut to
 * @param instructions one line a chamber, from the shoulder
 * @param calibration the machine offsets the lines already allow for
 */
public record CutCard(String family, Bitting bitting, List<CutInstruction> instructions, Calibration calibration) {

    public CutCard {
        instructions = List.copyOf(instructions);
    }

    /** A card for a machine that cuts true. */
    public CutCard(String family, Bitting bitting, List<CutInstruction> instructions) {
        this(family, bitting, instructions, Calibration.TRUE);
    }

    /** How many cuts the card holds. */
    public int cuts() {
        return instructions.size();
    }

    /** The card as printed: the family and bitting as a header, then a line a cut. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(family + "  " + bitting.render());
        instructions.stream().map(CutInstruction::render).forEach(out::add);
        return List.copyOf(out);
    }
}
