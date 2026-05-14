package com.jobpilot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_cache")
public class CompanyCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "company_name_en", length = 100)
    private String companyNameEn;

    @Column(name = "basic_info", columnDefinition = "JSON")
    private String basicInfo;

    @Column(name = "salary_data", columnDefinition = "JSON")
    private String salaryData;

    @Column(name = "review_summary", columnDefinition = "JSON")
    private String reviewSummary;

    @Column(name = "timeline", columnDefinition = "JSON")
    private String timeline;

    @Column(name = "data_source", length = 50)
    private String dataSource;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public CompanyCacheEntity() {
    }

    @PrePersist
    protected void onCreate() {
        this.lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyNameEn() {
        return companyNameEn;
    }

    public void setCompanyNameEn(String companyNameEn) {
        this.companyNameEn = companyNameEn;
    }

    public String getBasicInfo() {
        return basicInfo;
    }

    public void setBasicInfo(String basicInfo) {
        this.basicInfo = basicInfo;
    }

    public String getSalaryData() {
        return salaryData;
    }

    public void setSalaryData(String salaryData) {
        this.salaryData = salaryData;
    }

    public String getReviewSummary() {
        return reviewSummary;
    }

    public void setReviewSummary(String reviewSummary) {
        this.reviewSummary = reviewSummary;
    }

    public String getTimeline() {
        return timeline;
    }

    public void setTimeline(String timeline) {
        this.timeline = timeline;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
