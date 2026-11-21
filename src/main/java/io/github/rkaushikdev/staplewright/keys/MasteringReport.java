package io.github.rkaushikdev.staplewright.keys;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * How one key sits under another, chamber by chamber.
 *
 * <p>Every chamber the two keys disagree in is a split, and a split is a master wafer in the
 * cylinder. The chambers left over are maintained: both keys cut them the same and the cylinder
 * needs one pin there.
 *
 * @param splits the chambers where the keys disagree, bow first
 * @param chambers how many chambers each key has
 */
public record MasteringReport(List<ChamberSplit> splits, int chambers) {

    public MasteringReport {
        splits = List.copyOf(splits);
    }

    /** How many chambers split, which is how many extra wafers the cylinder carries. */
    public int splitCount() {
        return splits.size();
    }

    /** Chambers cut the same on both keys, which need no wafer. */
    public List<Integer> maintainedChambers() {
        Set<Integer> splitAt = splits.stream().map(ChamberSplit::chamber).collect(Collectors.toSet());
        return IntStream.rangeClosed(1, chambers)
                .filter(chamber -> !splitAt.contains(chamber))
                .boxed()
                .toList();
    }

    /** The chambers that split, bow first. */
    public List<Integer> splitChambers() {
        return splits.stream().map(ChamberSplit::chamber).toList();
    }

    /** The step in one chamber, nought where the two keys agree. */
    public int stepIn(int chamber) {
        return splits.stream()
                .filter(split -> split.chamber() == chamber)
                .findFirst()
                .map(ChamberSplit::step)
                .orElse(0);
    }

    /** Whether the two keys are cut the same everywhere. */
    public boolean isIdentical() {
        return splits.isEmpty();
    }
}
