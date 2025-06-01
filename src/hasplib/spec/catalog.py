"""The charts the shop actually stocks, written down as data.

Three manufacturers and five families, copied off the catalogues and checked
against a set of measured keys in 2019. They live here rather than in a table
because they are code from the shop's point of view: a chart changing means a
new supplier, which means a project, not an afternoon of data entry.

The pinning specs sit beside them because the two are always used together
and keeping them apart means somebody eventually pairs an A2 chart with a
conventional column height and wonders why the drivers do not fit.
"""

from __future__ import annotations

from dataclasses import dataclass

from hasplib.errors import NotFoundError
from hasplib.pinning.core import CoreSpec
from hasplib.pinning.stack import PinningSpec
from hasplib.spec.chart import DepthAndSpaceChart
from hasplib.spec.rules import CuttingRules

SARGENT_LA = DepthAndSpaceChart(
    manufacturer="Sargent",
    family="LA six pin",
    chambers=6,
    depths=tuple(range(10)),
    root_mils=328,
    increment_mils=15,
    first_space_mils=216,
    space_increment_mils=156,
    macs=7,
    notes="the academic buildings, mortise and rim",
)

SCHLAGE_C = DepthAndSpaceChart(
    manufacturer="Schlage",
    family="C keyway six pin",
    chambers=6,
    depths=tuple(range(10)),
    root_mils=335,
    increment_mils=15,
    first_space_mils=231,
    space_increment_mils=156,
    macs=7,
    notes="the residence halls before the 1998 core conversion",
)

BEST_A2 = DepthAndSpaceChart(
    manufacturer="Best",
    family="A2 seven pin core",
    chambers=7,
    depths=tuple(range(10)),
    root_mils=320,
    increment_mils=25,
    first_space_mils=150,
    space_increment_mils=155,
    macs=7,
    notes="every interchangeable core on campus",
)

CORBIN_60 = DepthAndSpaceChart(
    manufacturer="Corbin",
    family="60 keyway five pin",
    chambers=5,
    depths=(0, 1, 2, 3, 4, 5, 6),
    root_mils=328,
    increment_mils=20,
    first_space_mils=200,
    space_increment_mils=150,
    macs=4,
    notes="the maintenance padlocks, and nothing else since 1994",
)

SARGENT_LA_SIX_CHAMBER = DepthAndSpaceChart(
    manufacturer="Sargent",
    family="LA six pin, depth 4 withdrawn",
    chambers=6,
    depths=(0, 1, 2, 3, 5, 6, 7, 8, 9),
    root_mils=328,
    increment_mils=15,
    first_space_mils=216,
    space_increment_mils=156,
    macs=7,
    notes="after the 2011 recall, the factory stopped supplying a four",
)

CHARTS = (SARGENT_LA, SCHLAGE_C, BEST_A2, CORBIN_60, SARGENT_LA_SIX_CHAMBER)


@dataclass(frozen=True)
class Family:
    """A chart and everything that goes with it."""

    chart: DepthAndSpaceChart
    pinning: PinningSpec
    core: CoreSpec | None = None
    rules: CuttingRules = CuttingRules()

    @property
    def key(self) -> str:
        return f"{self.chart.manufacturer}/{self.chart.family}"

    @property
    def takes_cores(self) -> bool:
        return self.core is not None


def _pinning(chart: DepthAndSpaceChart, bottom: int, column: int, wafer: int) -> PinningSpec:
    return PinningSpec(chart, bottom, column, wafer)


FAMILIES = (
    Family(SARGENT_LA, _pinning(SARGENT_LA, 150, 480, 30)),
    Family(SCHLAGE_C, _pinning(SCHLAGE_C, 150, 480, 30)),
    Family(
        BEST_A2,
        _pinning(BEST_A2, 100, 600, 25),
        CoreSpec(_pinning(BEST_A2, 100, 600, 25), 50),
    ),
    Family(CORBIN_60, _pinning(CORBIN_60, 160, 470, 40)),
    Family(SARGENT_LA_SIX_CHAMBER, _pinning(SARGENT_LA_SIX_CHAMBER, 150, 480, 30)),
)


def catalogue() -> dict[str, Family]:
    """Every family the shop stocks, keyed the way a part number reads."""
    return {family.key: family for family in FAMILIES}


def family(manufacturer: str, name: str) -> Family:
    key = f"{manufacturer}/{name}"
    found = catalogue().get(key)
    if found is None:
        raise NotFoundError("that family is not one the shop stocks", family=key)
    return found


def families_for(manufacturer: str) -> tuple[Family, ...]:
    return tuple(item for item in FAMILIES if item.chart.manufacturer == manufacturer)


def manufacturers() -> tuple[str, ...]:
    return tuple(sorted({item.chart.manufacturer for item in FAMILIES}))


def core_families() -> tuple[Family, ...]:
    return tuple(item for item in FAMILIES if item.takes_cores)
