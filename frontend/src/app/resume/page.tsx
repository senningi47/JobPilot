"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import apiClient from "@/lib/api";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

interface ResumeItem {
  id: number;
  structuredData?: {
    basic_info?: { name?: string };
  };
  version?: number;
  createdAt?: string;
}

export default function ResumeListPage() {
  const [resumes, setResumes] = useState<ResumeItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchResumes = async () => {
      try {
        const res: any = await apiClient.get("/v1/resumes");
        setResumes(res.data ?? []);
      } catch {
        setResumes([]);
      } finally {
        setLoading(false);
      }
    };
    fetchResumes();
  }, []);

  return (
    <main className="container mx-auto px-4 py-10">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold">我的简历</h1>
        <Link href="/resume/upload">
          <Button>上传新简历</Button>
        </Link>
      </div>

      {loading ? (
        <div className="grid gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-20 rounded-xl" />
          ))}
        </div>
      ) : resumes.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <p className="text-lg mb-2">暂无简历</p>
          <p className="text-sm">点击上方按钮上传你的第一份简历</p>
        </div>
      ) : (
        <div className="grid gap-4">
          {resumes.map((resume) => (
            <Link key={resume.id} href={`/resume/${resume.id}`}>
              <Card className="hover:bg-muted/50 transition-colors cursor-pointer">
                <CardContent>
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">
                        {resume.structuredData?.basic_info?.name || "未命名简历"}
                      </p>
                      {resume.version !== undefined && (
                        <p className="text-xs text-muted-foreground mt-0.5">
                          版本 {resume.version}
                        </p>
                      )}
                    </div>
                    {resume.createdAt && (
                      <span className="text-xs text-muted-foreground shrink-0">
                        {new Date(resume.createdAt).toLocaleDateString("zh-CN")}
                      </span>
                    )}
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </main>
  );
}
