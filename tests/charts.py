"""Charts the tests cut against. Kept out of the library on purpose.

These are the three families the shop actually stocks, written the way the
catalogue writes them. Tests that want something impossible build it here
with overrides rather than inventing a fourth manufacturer.
"""

from hasplib.spec.chart import DepthAndSpaceChart


def six_pin_chart(**overrides) -> DepthAndSpaceChart:
    settings = {
        "manufacturer": "Schlage",
        "family": "C keyway, six pin",
        "chambers": 6,
        "depths": tuple(range(0, 10)),
        "root_mils": 335,
        "increment_mils": 15,
        "first_space_mils": 231,
        "space_increment_mils": 156,
        "macs": 7,
    }
    settings.update(overrides)
    return DepthAndSpaceChart(**settings)


def seven_pin_core_chart(**overrides) -> DepthAndSpaceChart:
    """A small format interchangeable core, which is what the dorms run."""
    settings = {
        "manufacturer": "Best",
        "family": "A2 seven pin core",
        "chambers": 7,
        "depths": tuple(range(0, 10)),
        "root_mils": 320,
        "increment_mils": 25,
        "first_space_mils": 150,
        "space_increment_mils": 155,
        "macs": 7,
        "notes": "depths quoted in the A2 system, twenty five mil increments",
    }
    settings.update(overrides)
    return DepthAndSpaceChart(**settings)


def five_pin_chart(**overrides) -> DepthAndSpaceChart:
    """The old maintenance padlocks, five chambers and a tight macs."""
    settings = {
        "manufacturer": "Corbin",
        "family": "60 keyway, five pin",
        "chambers": 5,
        "depths": (0, 1, 2, 3, 4, 5, 6),
        "root_mils": 328,
        "increment_mils": 20,
        "first_space_mils": 200,
        "space_increment_mils": 150,
        "macs": 4,
    }
    settings.update(overrides)
    return DepthAndSpaceChart(**settings)
