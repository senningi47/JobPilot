package com.jobpilot.service;

import java.util.List;
import java.util.Map;

public interface CompanyService {

    List<Map<String, Object>> searchCompanies(String query);

    Map<String, Object> getCompanyIntel(String name);

    Map<String, Object> refreshCompany(String name);
}
