"""A directory the tests can hand keys to."""

from datetime import date

from hasplib.control.people import Affiliation, Authorizer, Directory, Keyholder

TODAY = date(2025, 3, 4)


def a_directory() -> Directory:
    directory = Directory()
    directory.add(Keyholder("kmaki", "Kirsi Maki", Affiliation.FACULTY, "geography"))
    directory.add(Keyholder("dsalo", "Dan Salo", Affiliation.FACULTY, "geography"))
    directory.add(Keyholder("rvirta", "Riia Virta", Affiliation.STAFF, "geography"))
    directory.add(Keyholder("phamm", "Paul Hamm", Affiliation.STAFF, "facilities"))
    directory.add(
        Keyholder(
            "tkoski",
            "Tomas Koski",
            Affiliation.STUDENT,
            "geography",
            ends_on=date(2025, 5, 16),
        )
    )
    directory.add(
        Keyholder(
            "jwexler",
            "Jo Wexler",
            Affiliation.CONTRACTOR,
            "facilities",
            ends_on=date(2025, 1, 31),
        )
    )
    directory.add_authorizer(Authorizer("rvirta", "geography", ("MOO",)))
    directory.add_authorizer(Authorizer("phamm", "facilities", (), may_approve_masters=True))
    return directory
