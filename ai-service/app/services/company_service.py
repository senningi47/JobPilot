import json
import os

_COMPANY_DATA: list[dict] | None = None


def load_company_data() -> list[dict]:
    """Load company intelligence data from JSON file."""
    global _COMPANY_DATA
    if _COMPANY_DATA is not None:
        return _COMPANY_DATA

    data_path = os.path.join(
        os.path.dirname(__file__), "mock_data", "company_data.json"
    )
    with open(data_path, encoding="utf-8") as f:
        _COMPANY_DATA = json.load(f)
    return _COMPANY_DATA


def get_company(name: str) -> dict | None:
    """Find a company by name or name_en (case insensitive).
    Returns the full company dict or None if not found."""
    data = load_company_data()
    query = name.strip().lower()
    for company in data:
        if company["name"].lower() == query or company["name_en"].lower() == query:
            return company
    return None


def search_companies(query: str) -> list[dict]:
    """Search companies by name or name_en containing the query.
    Returns a list of {name, name_en, industry} for matches.
    Empty query returns empty list."""
    if not query:
        return []

    data = load_company_data()
    q = query.strip().lower()
    results = []
    for company in data:
        if q in company["name"].lower() or q in company["name_en"].lower():
            results.append({
                "name": company["name"],
                "name_en": company["name_en"],
                "industry": company["basic_info"]["industry"],
            })
    return results
