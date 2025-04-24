import pytest

from hasplib.errors import ConflictError, InputError
from hasplib.settings import DEFAULTS, PREFIX, ShopSettings, from_env, from_mapping


def env(**values) -> dict[str, str]:
    return {PREFIX + name: str(value) for name, value in values.items()}


def test_an_empty_mapping_gives_the_defaults():
    assert from_mapping({}) == DEFAULTS


def test_a_setting_absent_from_the_mapping_keeps_its_default():
    found = from_mapping(env(MAX_RUN=5))
    assert found.cutting.max_run == 5
    assert found.cutting.min_distinct == DEFAULTS.cutting.min_distinct


def test_the_deposits_come_off_the_mapping():
    found = from_mapping(env(DEPOSIT_CHANGE_CENTS=1000, DEPOSIT_MASTER_CENTS=9000))
    assert found.deposits.for_key(False) == 1000
    assert found.deposits.for_key(True) == 9000


def test_the_notice_ladder_comes_off_the_mapping():
    found = from_mapping(env(REMINDER_DAYS=1, FIRST_NOTICE_DAYS=2, SECOND_NOTICE_DAYS=3))
    assert found.notices.reminder_days == 1
    assert found.notices.second_days == 3


def test_a_notice_ladder_out_of_order_is_refused():
    with pytest.raises(ConflictError):
        from_mapping(env(REMINDER_DAYS=99))


def test_the_scope_flags_read_true_and_false():
    assert from_mapping(env(STRICT_REPIN="true")).scope.recut_all_on_repin
    assert not from_mapping(env(STRICT_REPIN="no")).scope.recut_all_on_repin
    assert from_mapping(env(REKEY_MASTERS="1")).scope.always_rekey_masters


@pytest.mark.parametrize("value", ["yes", "on", "TRUE", "1"])
def test_every_spelling_of_true(value):
    assert from_mapping(env(STRICT_REPIN=value)).scope.recut_all_on_repin


@pytest.mark.parametrize("value", ["no", "off", "FALSE", "0"])
def test_every_spelling_of_false(value):
    assert not from_mapping(env(STRICT_REPIN=value)).scope.recut_all_on_repin


def test_a_flag_that_is_neither_is_refused():
    with pytest.raises(InputError) as caught:
        from_mapping(env(STRICT_REPIN="perhaps"))
    assert caught.value.facts["setting"] == "STRICT_REPIN"


def test_a_number_that_is_not_a_number_is_refused():
    with pytest.raises(InputError) as caught:
        from_mapping(env(MAX_RUN="a few"))
    assert caught.value.facts["value"] == "a few"


def test_the_rate_card_comes_off_the_mapping():
    found = from_mapping(env(LABOUR_CENTS_HOUR=2600, BLANK_CENTS=90))
    assert found.rates.labour_cents_per_hour == 2600
    assert found.rates.blank_cents == 90


def test_the_quarantine_window_and_the_step():
    found = from_mapping(env(QUARANTINE_YEARS=3, PROGRESSION_STEP=1))
    assert found.quarantine_years == 3
    assert found.progression_step == 1


@pytest.mark.parametrize("broken", [{"quarantine_years": 0}, {"progression_step": 0}])
def test_settings_that_forbid_everything_are_refused(broken):
    with pytest.raises(InputError):
        ShopSettings(**broken)


def test_the_deposits_can_be_changed_without_touching_the_rest():
    found = DEFAULTS.with_deposits(1000, 9000)
    assert found.deposits.for_key(True) == 9000
    assert found.notices == DEFAULTS.notices


def test_a_negative_deposit_rate_is_refused():
    with pytest.raises(InputError):
        DEFAULTS.with_deposits(-1, 5000)


def test_the_description_names_every_group():
    lines = DEFAULTS.describe()
    assert len(lines) == 6
    assert any(line.startswith("deposits:") for line in lines)
    assert any(line.startswith("quarantine:") for line in lines)


def test_the_environment_is_read_the_same_way(monkeypatch):
    monkeypatch.setenv(PREFIX + "MAX_RUN", "5")
    assert from_env().cutting.max_run == 5


def test_an_environment_with_nothing_set_gives_the_defaults(monkeypatch):
    for name in list(DEFAULTS.describe()):
        assert name
    monkeypatch.delenv(PREFIX + "MAX_RUN", raising=False)
    assert from_env().cutting.max_run == DEFAULTS.cutting.max_run
