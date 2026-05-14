"use client";

import { useEffect, useState } from "react";
import apiClient from "@/lib/api";
import CategoryGrid from "@/components/explore/CategoryGrid";
import SearchBar from "@/components/explore/SearchBar";
import { Skeleton } from "@/components/ui/skeleton";
import { useRouter } from "next/navigation";

export default function ExplorePage() {
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const res: any = await apiClient.get("/v1/jobs/categories");
        setCategories(res.data ?? []);
      } catch {
        setCategories([]);
      } finally {
        setLoading(false);
      }
    };
    fetchCategories();
  }, []);

  const handleSearchSelect = (result: { job_title: string; major: string }) => {
    router.push(`/explore/major/${encodeURIComponent(result.major)}`);
  };

  return (
    <main className="container mx-auto px-4 py-10">
      <h1 className="text-3xl font-bold text-center mb-8">岗位探索</h1>
      <div className="mb-10">
        <SearchBar onSelect={handleSearchSelect} />
      </div>
      {loading ? (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      ) : (
        <CategoryGrid categories={categories} />
      )}
    </main>
  );
}
