package com.jobpilot.controller;

import com.jobpilot.dto.ApiResponse;
import com.jobpilot.service.CompanyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> searchCompanies(@RequestParam String q) {
        return ApiResponse.success(companyService.searchCompanies(q));
    }

    @GetMapping("/{name}")
    public ApiResponse<Map<String, Object>> getCompanyIntel(@PathVariable String name) {
        return ApiResponse.success(companyService.getCompanyIntel(name));
    }

    @PostMapping("/{name}/refresh")
    public ApiResponse<Map<String, Object>> refreshCompany(@PathVariable String name) {
        return ApiResponse.success(companyService.refreshCompany(name));
    }
}
