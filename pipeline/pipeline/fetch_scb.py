"""Fetch SCB (Statistics Sweden) baby names data from the PxWeb API.

This module provides functionality to fetch name statistics from Statistics Sweden's
official data API. Note that the API has limitations:

- The English endpoint (en/ssd) only lists old tables
- The Swedish endpoint (sv/ssd) may have more current tables
- Direct GET requests to name tables return "Bad Request"
- POST requests with JSON query format are required for table data
"""

import json
from pathlib import Path
from typing import Optional

import requests
from requests.exceptions import RequestException

from .config import SCB_API_BASE, SCB_BE_CATEGORY, SCB_DBID, SCB_NAME_STATISTICS, SCB_SWEDISH_LANG, SCB_TABLES


def fetch_scb_categories() -> dict:
    """
    Fetch the available categories under the BE (Population) section.

    Returns:
        Dictionary of category IDs and descriptions
    """
    url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}"
    response = requests.get(url)
    response.raise_for_status()
    return {item["id"]: item["text"] for item in response.json()}


def fetch_scb_tables() -> dict:
    """
    Fetch the available tables under BE0001 (Name statistics).

    Returns:
        Dictionary of table IDs and descriptions
    """
    url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}"
    response = requests.get(url)
    response.raise_for_status()
    return {item["id"]: item["text"] for item in response.json()}


def fetch_scb_table_metadata(table_id: str) -> dict:
    """
    Fetch metadata about a specific SCB table.

    Args:
        table_id: The table ID (e.g., "BE0001D")

    Returns:
        Dictionary containing table metadata
    """
    url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}/{table_id}"
    response = requests.get(url)
    response.raise_for_status()
    return response.json()


def query_scb_table(table_id: str, query: dict) -> dict:
    """
    Query a specific SCB table with JSON format.

    Args:
        table_id: The table ID
        query: The query dictionary in SCB API format

    Returns:
        Dictionary containing the query results
    """
    url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}/{table_id}"
    headers = {"Content-Type": "application/json"}
    response = requests.post(url, json=query, headers=headers)
    response.raise_for_status()
    return response.json()


def fetch_swedish_tables() -> dict:
    """
    Fetch tables from the Swedish language endpoint.

    Returns:
        Dictionary of table IDs and descriptions from Swedish endpoint
    """
    url = f"{SCB_API_BASE}/{SCB_SWEDISH_LANG}/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}"
    response = requests.get(url)
    response.raise_for_status()
    return {item["id"]: item["text"] for item in response.json()}


def fetch_scb_data() -> dict:
    """
    Fetch all available SCB data sources.

    Returns:
        Dictionary containing:
        - categories: BE categories
        - tables: Tables under BE0001
        - swedish_tables: Tables from Swedish endpoint
        - table_metadata: Metadata for each table
    """
    results = {
        "categories": {},
        "tables": {},
        "swedish_tables": {},
        "table_metadata": {},
        "api_base": SCB_API_BASE,
    }

    try:
        results["categories"] = fetch_scb_categories()
    except Exception as e:
        results["categories_error"] = str(e)

    try:
        results["tables"] = fetch_scb_tables()
    except Exception as e:
        results["tables_error"] = str(e)

    try:
        results["swedish_tables"] = fetch_swedish_tables()
    except Exception as e:
        results["swedish_tables_error"] = str(e)

    # Try to get metadata for known tables
    for table_id in SCB_TABLES.keys():
        try:
            results["table_metadata"][table_id] = fetch_scb_table_metadata(table_id)
        except Exception as e:
            results["table_metadata"][table_id] = {"error": str(e)}

    return results


def verify_api_access() -> dict:
    """
    Verify the SCB API accessibility and document findings.

    Returns:
        Dictionary with verification results
    """
    results = {
        "api_available": False,
        "english_endpoint_works": False,
        "swedish_endpoint_works": False,
        "name_tables_accessible": False,
        "findings": [],
    }

    # Test English root
    try:
        url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}"
        response = requests.get(url)
        if response.status_code == 200:
            results["english_endpoint_works"] = True
            results["findings"].append("English endpoint (en/ssd) returns 200/JSON")
    except Exception as e:
        results["findings"].append(f"English endpoint failed: {e}")

    # Test Swedish root
    try:
        url = f"{SCB_API_BASE}/{SCB_SWEDISH_LANG}/ssd/{SCB_DBID}"
        response = requests.get(url)
        if response.status_code == 200:
            results["swedish_endpoint_works"] = True
            results["findings"].append("Swedish endpoint (sv/ssd) returns 200/JSON")
    except Exception as e:
        results["findings"].append(f"Swedish endpoint failed: {e}")

    # Test name statistics
    try:
        url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}"
        response = requests.get(url)
        if response.status_code == 200:
            data = response.json()
            results["findings"].append(f"Name statistics listed: {len(data)} tables")
            results["tables"] = [t["id"] for t in data]
            results["api_available"] = True
    except Exception as e:
        results["findings"].append(f"Name statistics failed: {e}")

    # Test table access
    for table_id in SCB_TABLES.keys():
        try:
            url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}/{table_id}"
            response = requests.get(url)
            if response.status_code == 200:
                results["findings"].append(f"GET {table_id}: 200 OK")
            elif response.status_code == 400:
                results["findings"].append(f"GET {table_id}: 400 Bad Request")
            else:
                results["findings"].append(f"GET {table_id}: {response.status_code}")
        except Exception as e:
            results["findings"].append(f"GET {table_id}: {e}")

    # Test POST query
    try:
        url = f"{SCB_API_BASE}/en/ssd/{SCB_DBID}/{SCB_BE_CATEGORY}/{SCB_NAME_STATISTICS}/BE0001D"
        headers = {"Content-Type": "application/json"}
        query = {"query": [], "response": {"format": "json"}}
        response = requests.post(url, json=query, headers=headers)
        if response.status_code == 200:
            results["findings"].append("POST query to BE0001D: 200 OK")
            results["name_tables_accessible"] = True
        else:
            results["findings"].append(f"POST query to BE0001D: {response.status_code}")
    except Exception as e:
        results["findings"].append(f"POST query failed: {e}")

    return results


def download_scb_archive() -> Path:
    """
    Download SCB data archive.

    Returns:
        Path to downloaded archive

    Note: This method may not work if the API doesn't support direct downloads.
    Manual download from Statistics Sweden website may be required.
    """
    raise NotImplementedError(
        "SCB data archive download is not implemented. "
        "Please download data manually from https://www.scb.se/en/understand-more/population/name-statistics/"
    )
