"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import apiClient from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import SalaryChart from "@/components/company/SalaryChart";
import ReviewRadar from "@/components/company/ReviewRadar";

interface SalaryData {
  p25: number;
  p50: number;
  p75: number;
  currency: string;
}

interface ReviewDimension {
  name: string;
  score: number;
  summary?: string;
}

interface TimelineItem {
  date: string;
  title: string;
  description?: string;
}

interface NewsItem {
  title: string;
  url?: string;
  date?: string;
}

interface CompanyDetail {
  name: string;
  name_en?: string;
  industry?: string;
  funding_stage?: string;
  scale?: string;
  location?: string;
  salary: SalaryData;
  reviews: ReviewDimension[];
  timeline: TimelineItem[];
  news: NewsItem[];
}

export default function CompanyDetailPage() {
  const params = useParams();
  const companyName = decodeURIComponent(params.name as string);
  const [company, setCompany] = useState<CompanyDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!companyName) return;

    const fetchCompany = async () => {
      try {
        const res: any = await apiClient.get(
          `/v1/companies/${encodeURIComponent(companyName)}`
        );
        setCompany(res.data ?? null);
      } catch {
        setCompany(null);
      } finally {
        setLoading(false);
      }
    };
    fetchCompany();
  }, [companyName]);

  if (loading) {
    return (
      <main className="container mx-auto px-4 py-10">
        <Skeleton className="h-9 w-64 mb-4" />
        <Skeleton className="h-5 w-48 mb-8" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Skeleton className="h-72 rounded-xl" />
          <Skeleton className="h-72 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!company) {
    return (
      <main className="container mx-auto px-4 py-10">
        <p className="text-muted-foreground text-center py-12">
          未找到该公司数据
        </p>
      </main>
    );
  }

  return (
    <main className="container mx-auto px-4 py-10">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold">
          {company.name}
          {company.name_en && (
            <span className="ml-2 text-base font-normal text-muted-foreground">
              {company.name_en}
            </span>
          )}
        </h1>
        {company.industry && (
          <p className="text-muted-foreground mt-1">{company.industry}</p>
        )}
        <div className="flex flex-wrap gap-2 mt-3">
          {company.funding_stage && (
            <Badge variant="secondary">{company.funding_stage}</Badge>
          )}
          {company.scale && (
            <Badge variant="outline">{company.scale}</Badge>
          )}
          {company.location && (
            <Badge variant="outline">{company.location}</Badge>
          )}
        </div>
      </div>

      {/* Charts: Salary + Review */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        <Card>
          <CardHeader>
            <CardTitle>薪资分布</CardTitle>
          </CardHeader>
          <CardContent>
            <SalaryChart salaryData={company.salary} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>员工评价</CardTitle>
          </CardHeader>
          <CardContent>
            <ReviewRadar dimensions={company.reviews} />
          </CardContent>
        </Card>
      </div>

      {/* Timeline */}
      {company.timeline && company.timeline.length > 0 && (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle>公司大事记</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="relative border-l-2 border-muted pl-6 space-y-6">
              {company.timeline.map((item, i) => (
                <div key={i} className="relative">
                  <div className="absolute -left-[31px] top-1 h-3 w-3 rounded-full bg-primary" />
                  <p className="text-xs text-muted-foreground mb-1">
                    {item.date}
                  </p>
                  <p className="font-medium text-sm">{item.title}</p>
                  {item.description && (
                    <p className="text-sm text-muted-foreground mt-1">
                      {item.description}
                    </p>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* News */}
      {company.news && company.news.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>近期动态</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {company.news.map((n, i) => (
                <li key={i} className="flex items-start gap-2">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-muted-foreground shrink-0" />
                  <div>
                    {n.url ? (
                      <a
                        href={n.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-sm font-medium hover:underline"
                      >
                        {n.title}
                      </a>
                    ) : (
                      <span className="text-sm font-medium">{n.title}</span>
                    )}
                    {n.date && (
                      <span className="text-xs text-muted-foreground ml-2">
                        {n.date}
                      </span>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </main>
  );
}
