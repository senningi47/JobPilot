import json
import os
import random

_KNOWLEDGE_GRAPH: list[dict] | None = None


def load_knowledge_graph() -> list[dict]:
    """Load job knowledge graph from JSON file."""
    global _KNOWLEDGE_GRAPH
    if _KNOWLEDGE_GRAPH is not None:
        return _KNOWLEDGE_GRAPH

    data_path = os.path.join(
        os.path.dirname(__file__), "mock_data", "job_knowledge_graph.json"
    )
    with open(data_path, encoding="utf-8") as f:
        _KNOWLEDGE_GRAPH = json.load(f)
    return _KNOWLEDGE_GRAPH


def get_categories() -> list[str]:
    """Return sorted unique category names."""
    graph = load_knowledge_graph()
    categories = sorted({entry["category"] for entry in graph})
    return categories


def get_majors_by_category(category: str) -> list[dict]:
    """Return list of {major, category} filtered by category."""
    graph = load_knowledge_graph()
    return [
        {"major": entry["major"], "category": entry["category"]}
        for entry in graph
        if entry["category"] == category
    ]


def get_major_detail(major: str) -> dict | None:
    """Return full major detail or None if not found."""
    graph = load_knowledge_graph()
    for entry in graph:
        if entry["major"] == major:
            return entry
    return None


def search_jobs(query: str) -> list[dict]:
    """Keyword search across job_title, tags, description in all majors.
    Returns top 5 results with random confidence scores 0.6-0.99.
    Empty query returns empty list."""
    if not query:
        return []

    query = query[:200]
    graph = load_knowledge_graph()
    results: list[dict] = []

    for entry in graph:
        major = entry["major"]
        category = entry["category"]
        for job_list_key in ("primary_jobs", "extended_jobs"):
            for job in entry[job_list_key]:
                searchable = " ".join([
                    job["job_title"],
                    " ".join(job["tags"]),
                    job.get("description", ""),
                ])
                if query in searchable:
                    results.append({
                        "job_title": job["job_title"],
                        "major": major,
                        "category": category,
                        "tags": job["tags"],
                        "description": job.get("description", ""),
                        "confidence": round(random.uniform(0.6, 0.99), 2),
                    })

    return results[:5]
