"""Tests for the SCB fetch module."""

from unittest import mock

import pytest

from pipeline.fetch_scb import (
    fetch_scb_categories,
    fetch_scb_data,
    fetch_scb_tables,
    fetch_swedish_tables,
    query_scb_table,
    verify_api_access,
)


class TestSCBAPI:
    """Tests for the SCB API functionality."""

    @mock.patch("pipeline.fetch_scb.requests.get")
    def test_fetch_scb_categories(self, mock_get):
        """Test fetching SCB categories."""
        mock_response = mock.MagicMock()
        mock_response.json.return_value = [
            {"id": "BE0001", "text": "Name statistics"},
            {"id": "BE0101", "text": "Population statistics"},
        ]
        mock_response.raise_for_status = mock.MagicMock()
        mock_get.return_value = mock_response

        categories = fetch_scb_categories()
        assert "BE0001" in categories
        assert categories["BE0001"] == "Name statistics"

    @mock.patch("pipeline.fetch_scb.requests.get")
    def test_fetch_scb_tables(self, mock_get):
        """Test fetching SCB tables under BE0001."""
        mock_response = mock.MagicMock()
        mock_response.json.return_value = [
            {"id": "BE0001D", "text": "Newborn - Old tables not updated"},
            {"id": "BE0001G", "text": "All registered persons - Old tables not updated"},
        ]
        mock_response.raise_for_status = mock.MagicMock()
        mock_get.return_value = mock_response

        tables = fetch_scb_tables()
        assert len(tables) > 0
        assert "BE0001D" in tables

    @mock.patch("pipeline.fetch_scb.requests.get")
    def test_fetch_swedish_tables(self, mock_get):
        """Test fetching tables from Swedish endpoint."""
        mock_response = mock.MagicMock()
        mock_response.json.return_value = [
            {"id": "BE0001D", "text": "Nyfödda - Äldre tabeller"},
        ]
        mock_response.raise_for_status = mock.MagicMock()
        mock_get.return_value = mock_response

        tables = fetch_swedish_tables()
        assert len(tables) > 0
        assert "BE0001D" in tables

    @mock.patch("pipeline.fetch_scb.requests.get")
    def test_verify_api_access(self, mock_get):
        """Test API access verification."""
        # Mock multiple responses
        mock_responses = [
            mock.MagicMock(  # English root
                status_code=200,
                json=lambda: [{"dbid": "ssd", "text": "Statistics Sweden"}],
                raise_for_status=mock.MagicMock(),
            ),
            mock.MagicMock(  # Name statistics
                status_code=200,
                json=lambda: [{"id": "BE0001D", "text": "Old tables"}],
                raise_for_status=mock.MagicMock(),
            ),
        ]
        mock_get.side_effect = mock_responses

        results = verify_api_access()
        assert "api_available" in results
        assert "findings" in results
        assert len(results["findings"]) > 0

    @mock.patch("pipeline.fetch_scb.requests.post")
    def test_query_scb_table(self, mock_post):
        """Test querying SCB table with POST request."""
        mock_response = mock.MagicMock()
        mock_response.json.return_value = {
            "columns": [{"code": "Tid", "text": "year"}],
            "data": [],
        }
        mock_response.raise_for_status = mock.MagicMock()
        mock_post.return_value = mock_response

        query = {"query": [], "response": {"format": "json"}}
        result = query_scb_table("BE0101H", query)

        assert "columns" in result


class TestSCBConfiguration:
    """Tests for SCB configuration values."""

    def test_scb_config_values(self):
        """Test that SCB configuration values are set correctly."""
        from pipeline import config

        assert config.SCB_API_BASE == "https://api.scb.se/OV0104/v1/doris"
        assert config.SCB_DBID == "ssd"
        assert config.SCB_BE_CATEGORY == "BE"
        assert config.SCB_NAME_STATISTICS == "BE0001"
        assert config.SWEDEN_COUNTRY_CODE == "SE"

    def test_scb_tables_defined(self):
        """Test that SCB tables are defined."""
        from pipeline import fetch_scb

        tables = fetch_scb.SCB_TABLES
        assert "BE0001D" in tables
        assert "BE0001G" in tables


class TestNetworkIntegration:
    """Integration tests for SCB API (skipped if network unavailable)."""

    @pytest.mark.skip(reason="Network-dependent test - run manually to verify API")
    def test_real_api_categories(self):
        """Test real API categories endpoint."""
        categories = fetch_scb_categories()
        assert "BE0001" in categories

    @pytest.mark.skip(reason="Network-dependent test - run manually to verify API")
    def test_real_api_tables(self):
        """Test real API tables endpoint."""
        tables = fetch_scb_tables()
        assert len(tables) > 0
