"use client";

import Link from "next/link";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const CATEGORY_ICONS: Record<string, string> = {
  工学: "\u{1F527}",
  理学: "\u{1F52C}",
  经济学: "\u{1F4CA}",
  管理学: "\u{1F4CB}",
  文学: "\u{1F4D6}",
};

const FALLBACK_ICON = "\u{1F393}";

interface CategoryGridProps {
  categories: string[];
}

export default function CategoryGrid({ categories }: CategoryGridProps) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
      {categories.map((category) => (
        <Link key={category} href={`/explore/${encodeURIComponent(category)}`}>
          <Card
            className={cn(
              "hover:shadow-md transition-shadow cursor-pointer h-full",
              "hover:ring-2 hover:ring-primary/40"
            )}
          >
            <CardContent className="flex flex-col items-center justify-center py-8 gap-3">
              <span className="text-4xl" role="img" aria-label={category}>
                {CATEGORY_ICONS[category] ?? FALLBACK_ICON}
              </span>
              <span className="text-base font-medium text-center">
                {category}
              </span>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  );
}
