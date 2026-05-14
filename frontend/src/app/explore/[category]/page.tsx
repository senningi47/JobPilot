"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import apiClient from "@/lib/api";
import MajorCard from "@/components/explore/MajorCard";
import { Skeleton } from "@/components/ui/skeleton";

interface Major {
  major: string;
  category: string;
}

export default function CategoryPage() {
  const params = useParams();
  const category = decodeURIComponent(params.category as string);
  const [majors, setMajors] = useState<Major[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!category) return;

    const fetchMajors = async () => {
      try {
        const res: any = await apiClient.get(
          `/v1/jobs/categories/${encodeURIComponent(category)}/majors`
        );
        setMajors(res.data ?? []);
      } catch {
        setMajors([]);
      } finally {
        setLoading(false);
      }
    };
    fetchMajors();
  }, [category]);

  return (
    <main className="container mx-auto px-4 py-10">
      <h1 className="text-2xl font-bold mb-8">{category}</h1>
      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      ) : majors.length === 0 ? (
        <p className="text-muted-foreground text-center py-12">
          暂无该类别下的专业数据
        </p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {majors.map((m) => (
            <MajorCard key={m.major} major={m} />
          ))}
        </div>
      )}
    </main>
  );
}
