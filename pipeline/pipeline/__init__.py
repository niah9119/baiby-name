"""Pipeline package for importing official statistics into the database."""

from . import classify_style
from . import config
from . import fetch
from . import fetch_ons
from . import fetch_scb
from . import fetch_ssb
from . import fetch_wikidata
from . import load
from . import normalize
from . import normalize_ons
from . import normalize_scb
from . import normalize_ssb
from . import normalize_wikidata

__all__ = [
    "classify_style",
    "config",
    "fetch",
    "fetch_ons",
    "fetch_scb",
    "fetch_ssb",
    "fetch_wikidata",
    "load",
    "normalize",
    "normalize_ons",
    "normalize_scb",
    "normalize_ssb",
    "normalize_wikidata",
]
