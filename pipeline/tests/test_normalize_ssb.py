"""Tests for the Norway (SSB) normalizer.

Two encoding details in the source are easy to get wrong and fail silently, so they
are covered explicitly:

* sex lives in the first character of the name code ("1" girls, "2" boys) -- getting
  this wrong merges the sexes into a single series;
* the codes are ASCII-only, with "Z2" for "Ø" and "Z3" for "Å" -- deriving the name
  from the code stores "BJZ2RG" instead of "Bjørg".
"""

import json

import pytest

from pipeline.normalize_ssb import (
    _records_from_payload,
    _sex_from_code,
    normalize_ssb_dataset,
)


def _payload(names, years, values):
    """Build a json-stat2 payload shaped like SSB's.

    `names` maps code -> label, `values` is laid out as [name][year] row-major, which
    matches the dimension order ['Fornavn', 'ContentsCode', 'Tid'].
    """
    flat = [v for row in values for v in row]
    return {
        "id": ["Fornavn", "ContentsCode", "Tid"],
        "size": [len(names), 1, len(years)],
        "value": flat,
        "dimension": {
            "Fornavn": {
                "category": {
                    "index": {code: i for i, (code, _label) in enumerate(names)},
                    "label": dict(names),
                }
            },
            "ContentsCode": {"category": {"index": {"Personer": 0}}},
            "Tid": {"category": {"index": {y: i for i, y in enumerate(years)}}},
        },
    }


class TestSexFromCode:
    def test_prefix_one_is_a_girl(self):
        assert _sex_from_code("1EMMA") == "Girl"

    def test_prefix_two_is_a_boy(self):
        assert _sex_from_code("2JAKOB") == "Boy"

    def test_unknown_prefix_is_rejected(self):
        assert _sex_from_code("9WHAT") is None


class TestRecordDecoding:
    def test_maps_names_years_and_counts(self):
        payload = _payload(
            [("1EMMA", "Emma"), ("2JAKOB", "Jakob")],
            ["2023", "2024"],
            [[10, 379], [5, 261]],
        )

        records = _records_from_payload(payload)

        assert {(r["name"], r["year"], r["count"], r["sex"]) for r in records} == {
            ("Emma", 2023, 10, "Girl"),
            ("Emma", 2024, 379, "Girl"),
            ("Jakob", 2023, 5, "Boy"),
            ("Jakob", 2024, 261, "Boy"),
        }
        assert {r["country"] for r in records} == {"NO"}

    def test_uses_label_not_the_ascii_code(self):
        """1BJZ2RG must become Bjørg -- Z2 stands for the letter o-slash."""
        payload = _payload([("1BJZ2RG", "Bjørg")], ["2024"], [[678]])

        records = _records_from_payload(payload)

        assert records[0]["name"] == "Bjørg"
        assert "Z2" not in records[0]["name"]

    def test_sexes_are_not_merged(self):
        """A name spelled the same for both sexes must stay two series."""
        payload = _payload(
            [("1KIM", "Kim"), ("2KIM", "Kim")], ["2024"], [[40], [60]]
        )

        records = _records_from_payload(payload)

        by_sex = {r["sex"]: r["count"] for r in records}
        assert by_sex == {"Girl": 40, "Boy": 60}

    def test_null_years_are_skipped(self):
        """Years before a name was registered come back null, not zero."""
        payload = _payload([("1EMMA", "Emma")], ["1900", "2024"], [[None, 379]])

        records = _records_from_payload(payload)

        assert len(records) == 1
        assert records[0]["year"] == 2024

    def test_zero_counts_are_skipped(self):
        payload = _payload([("1EMMA", "Emma")], ["2024"], [[0]])

        assert _records_from_payload(payload) == []

    def test_unknown_prefix_rows_are_dropped(self):
        payload = _payload([("9ODD", "Odd")], ["2024"], [[10]])

        assert _records_from_payload(payload) == []


class TestRanking:
    def test_rank_is_per_year_and_sex(self, tmp_path):
        payload = _payload(
            [("1EMMA", "Emma"), ("1NORA", "Nora"), ("2JAKOB", "Jakob")],
            ["2024"],
            [[379], [366], [261]],
        )
        p = tmp_path / "ssb.json"
        p.write_text(json.dumps(payload), encoding="utf-8")

        df = normalize_ssb_dataset(p)

        ranks = {(r["name"], r["sex"]): r["rank"] for _, r in df.iterrows()}
        # Jakob is rank 1 among boys despite the lowest count overall.
        assert ranks[("Emma", "Girl")] == 1
        assert ranks[("Nora", "Girl")] == 2
        assert ranks[("Jakob", "Boy")] == 1

    def test_ties_share_a_rank(self, tmp_path):
        payload = _payload(
            [("1ADA", "Ada"), ("1BEA", "Bea"), ("1CIA", "Cia")],
            ["2024"],
            [[100], [100], [50]],
        )
        p = tmp_path / "ssb.json"
        p.write_text(json.dumps(payload), encoding="utf-8")

        df = normalize_ssb_dataset(p)

        ranks = dict(zip(df["name"], df["rank"]))
        assert ranks["Ada"] == ranks["Bea"] == 1
        assert ranks["Cia"] == 3  # "min" method: the tie consumes rank 2

    def test_canonical_columns(self, tmp_path):
        payload = _payload([("1EMMA", "Emma")], ["2024"], [[379]])
        p = tmp_path / "ssb.json"
        p.write_text(json.dumps(payload), encoding="utf-8")

        df = normalize_ssb_dataset(p)

        assert list(df.columns) == ["name", "country", "sex", "year", "count", "rank"]
