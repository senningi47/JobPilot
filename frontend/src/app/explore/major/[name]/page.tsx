"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import apiClient from "@/lib/api";
import JobTagCard from "@/components/explore/JobTagCard";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";

interface Job {
  job_title: string;
  tags: string[];
  salary_range_p50?: string;
  difficulty?: string;
  description?: string;
  typical_companies?: string[];
}

interface MajorDetail {
  major: string;
  category: string;
  primary_jobs: Job[];
  extended_jobs: Job[];
}

export default function MajorPage() {
  const params = useParams();
  const majorName = decodeURIComponent(params.name as string);
  const [detail, setDetail] = useState<MajorDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!majorName) return;

    const fetchDetail = async () => {
      try {
        const res: any = await apiClient.get(
          `/v1/jobs/majors/${encodeURIComponent(majorName)}`
        );
        setDetail(res.data ?? null);
      } catch {
        setDetail(null);
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [majorName]);

  if (loading) {
    return (
      <main className="container mx-auto px-4 py-10">
        <Skeleton className="h-9 w-64 mb-8" />
        <Skeleton className="h-10 w-48 mb-6" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-48 rounded-xl" />
          ))}
        </div>
      </main>
    );
  }

  if (!detail) {
    return (
      <main className="container mx-auto px-4 py-10">
        <p className="text-muted-foreground text-center py-12">
          未找到该专业的数据
        </p>
      </main>
    );
  }

  return (
    <main className="container mx-auto px-4 py-10">
      <div className="flex items-center gap-3 mb-8">
        <h1 className="text-2xl font-bold">{detail.major}</h1>
        <Badge variant="secondary">{detail.category}</Badge>
      </div>

      <Tabs defaultValue="primary">
        <TabsList>
          <TabsTrigger value="primary">主推岗位</TabsTrigger>
          <TabsTrigger value="extended">延伸岗位</TabsTrigger>
        </TabsList>

        <TabsContent value="primary">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
            {detail.primary_jobs.map((job) => (
              <JobTagCard key={job.job_title} job={job} />
            ))}
          </div>
          {detail.primary_jobs.length === 0 && (
            <p className="text-muted-foreground text-center py-8">
              暂无主推岗位数据
            </p>
          )}
        </TabsContent>

        <TabsContent value="extended">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
            {detail.extended_jobs.map((job) => (
              <JobTagCard key={job.job_title} job={job} />
            ))}
          </div>
          {detail.extended_jobs.length === 0 && (
            <p className="text-muted-foreground text-center py-8">
              暂无延伸岗位数据
            </p>
          )}
        </TabsContent>
      </Tabs>
    </main>
  );
}
