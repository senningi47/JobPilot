"use client";

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

interface Job {
  job_title: string;
  tags: string[];
  salary_range_p50?: string;
  difficulty?: string;
  description?: string;
}

interface JobTagCardProps {
  job: Job;
}

const DIFFICULTY_VARIANT: Record<string, "destructive" | "default" | "secondary"> = {
  "高": "destructive",
  "中": "default",
  "低": "secondary",
};

export default function JobTagCard({ job }: JobTagCardProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="text-base leading-snug">
            {job.job_title}
          </CardTitle>
          {job.difficulty && (
            <Badge
              variant={DIFFICULTY_VARIANT[job.difficulty] ?? "default"}
              className="shrink-0"
            >
              {job.difficulty}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-1.5">
          {job.tags.map((tag) => (
            <Badge key={tag} variant="outline">
              {tag}
            </Badge>
          ))}
        </div>
        {job.salary_range_p50 && (
          <p className="text-sm font-semibold text-green-600">
            {job.salary_range_p50}
          </p>
        )}
        {job.description && (
          <CardDescription className="line-clamp-2">
            {job.description}
          </CardDescription>
        )}
      </CardContent>
    </Card>
  );
}
