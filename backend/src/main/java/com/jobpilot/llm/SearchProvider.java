package com.jobpilot.llm;

import java.util.List;

public interface SearchProvider {

    List<SearchResult> search(String query, int maxResults);

    String getProviderName();

    boolean testConnection();

    record SearchResult(String title, String url, String snippet, String publishedDate) {}
}
