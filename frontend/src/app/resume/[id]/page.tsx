"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import apiClient from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import ResumePreview from "@/components/resume/ResumePreview";
import ScoreRadar from "@/components/resume/ScoreRadar";

interface AnalysisDimension {
  name: string;
  score: number;
  feedback: string;
}

interface AnalysisResult {
  overall_match: number;
  dimensions: AnalysisDimension[];
  highlights: string[];
  weaknesses: string[];
  suggestions: string[];
}

interface Resume {
  id: number;
  structuredData?: any;
  targetPosition?: string;
  version?: number;
}

export default function ResumeDetailPage() {
  const params = useParams();
  const resumeId = params.id as string;
  const [resume, setResume] = useState<Resume | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!resumeId) return;

    const fetchResume = async () => {
      try {
        const res: any = await apiClient.get(`/v1/resumes/${resumeId}`);
        setResume(res.data ?? null);
      } catch {
        setResume(null);
      } finally {
        setLoading(false);
      }
    };
    fetchResume();
  }, [resumeId]);

  const handleAnalyze = async () => {
    if (!resume) return;
    setAnalyzing(true);
    setError(null);

    try {
      const targetPosition =
        resume.structuredData?.basic_info?.target_position || "后端开发工程师";
      const res: any = await apiClient.post(
        `/v1/resumes/${resumeId}/analyze?targetPosition=${encodeURIComponent(targetPosition)}`
      );
      setAnalysis(res.data ?? null);
    } catch (err: any) {
      setError(err.response?.data?.message || "分析失败，请重试");
    } finally {
      setAnalyzing(false);
    }
  };

  if (loading) {
    return (
      <main className="container mx-auto px-4 py-10">
        <Skeleton className="h-9 w-48 mb-6" />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Skeleton className="h-96 rounded-xl" />
          <Skeleton className="h-96 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!resume) {
    return (
      <main className="container mx-auto px-4 py-10">
        <p className="text-muted-foreground text-center py-12">
          未找到该简历
        </p>
      </main>
    );
  }

  return (
    <main className="container mx-auto px-4 py-10">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">
          {resume.structuredData?.basic_info?.name || "简历详情"}
        </h1>
        <Button onClick={handleAnalyze} disabled={analyzing}>
          {analyzing ? "分析中..." : "分析匹配度"}
        </Button>
      </div>

      {error && (
        <p className="text-sm text-destructive mb-4">{error}</p>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: Resume Preview */}
        <div>
          {resume.structuredData && (
            <ResumePreview data={resume.structuredData} />
          )}
        </div>

        {/* Right: Analysis Results */}
        <div className="space-y-4">
          {!analysis && !analyzing && (
            <Card className="flex items-center justify-center min-h-[300px]">
              <p className="text-muted-foreground text-sm">
                点击「分析匹配度」查看分析结果
              </p>
            </Card>
          )}

          {analysis && (
            <>
              {/* Radar Chart */}
              <Card>
                <CardHeader>
                  <CardTitle>匹配度分析</CardTitle>
                </CardHeader>
                <CardContent>
                  <ScoreRadar
                    dimensions={analysis.dimensions}
                    overallMatch={analysis.overall_match}
                  />
                </CardContent>
              </Card>

              {/* Highlights */}
              {analysis.highlights && analysis.highlights.length > 0 && (
                <Card>
                  <CardHeader>
                    <CardTitle>优势</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <ul className="space-y-2">
                      {analysis.highlights.map((h, i) => (
                        <li key={i} className="text-sm flex gap-2">
                          <span className="shrink-0">&#10003;</span>
                          <span>{h}</span>
                        </li>
                      ))}
                    </ul>
                  </CardContent>
                </Card>
              )}

              {/* Weaknesses */}
              {analysis.weaknesses && analysis.weaknesses.length > 0 && (
                <Card>
                  <CardHeader>
                    <CardTitle>不足</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <ul className="space-y-2">
                      {analysis.weaknesses.map((w, i) => (
                        <li key={i} className="text-sm flex gap-2">
                          <span className="shrink-0">&#9888;</span>
                          <span>{w}</span>
                        </li>
                      ))}
                    </ul>
                  </CardContent>
                </Card>
              )}

              {/* Suggestions */}
              {analysis.suggestions && analysis.suggestions.length > 0 && (
                <Card>
                  <CardHeader>
                    <CardTitle>建议</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <ol className="space-y-2">
                      {analysis.suggestions.map((s, i) => (
                        <li key={i} className="text-sm flex gap-2">
                          <span className="text-muted-foreground shrink-0 font-medium">
                            {i + 1}.
                          </span>
                          <span>{s}</span>
                        </li>
                      ))}
                    </ol>
                  </CardContent>
                </Card>
              )}
            </>
          )}
        </div>
      </div>
    </main>
  );
}
