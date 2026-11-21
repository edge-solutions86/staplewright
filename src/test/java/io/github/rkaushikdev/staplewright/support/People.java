package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.control.Affiliation;
import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import java.time.LocalDate;
import java.util.List;

/**
 * A directory the tests can hand keys to.
 *
 * <p>Four permanent people across geography and facilities, a student whose record runs into May,
 * and a contractor whose record ran out at the end of January and was never closed. Two of them
 * sign: one for a single geography building, one for every facilities building and for masters.
 */
public final class People {

    /** The day the tests stand on. */
    public static final LocalDate TODAY = LocalDate.of(2025, 3, 4);

    private People() {}

    public static Directory aDirectory() {
        Directory directory = new Directory();
        directory.add(new Keyholder("kmaki", "Kirsi Maki", Affiliation.FACULTY, "geography"));
        directory.add(new Keyholder("dsalo", "Dan Salo", Affiliation.FACULTY, "geography"));
        directory.add(new Keyholder("rvirta", "Riia Virta", Affiliation.STAFF, "geography"));
        directory.add(new Keyholder("phamm", "Paul Hamm", Affiliation.STAFF, "facilities"));
        directory.add(
                new Keyholder("tkoski", "Tomas Koski", Affiliation.STUDENT, "geography", LocalDate.of(2025, 5, 16)));
        directory.add(
                new Keyholder("jwexler", "Jo Wexler", Affiliation.CONTRACTOR, "facilities", LocalDate.of(2025, 1, 31)));
        directory.addAuthorizer(new Authorizer("rvirta", "geography", List.of("MOO")));
        directory.addAuthorizer(new Authorizer("phamm", "facilities", List.of(), true));
        return directory;
    }
}
