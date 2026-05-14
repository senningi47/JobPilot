"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import apiClient from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface SearchResult {
  job_title: string;
  major: string;
  category: string;
  tags: string[];
  description?: string;
  confidence?: number;
}

interface SearchBarProps {
  onSelect?: (result: SearchResult) => void;
}

export default function SearchBar({ onSelect }: SearchBarProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [open, setOpen] = useState(false);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Debounced search
  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setOpen(false);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res: any = await apiClient.get(
          `/v1/jobs/search?q=${encodeURIComponent(query.trim())}`
        );
        const data: SearchResult[] = res.data ?? [];
        setResults(data);
        setOpen(data.length > 0);
      } catch {
        setResults([]);
        setOpen(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [query]);

  const handleBlur = useCallback(() => {
    blurTimer.current = setTimeout(() => setOpen(false), 200);
  }, []);

  const handleFocus = useCallback(() => {
    if (blurTimer.current) clearTimeout(blurTimer.current);
    if (results.length > 0) setOpen(true);
  }, [results.length]);

  const handleSelect = useCallback(
    (result: SearchResult) => {
      onSelect?.(result);
      setQuery(result.job_title);
      setOpen(false);
    },
    [onSelect]
  );

  return (
    <div ref={containerRef} className="relative w-full max-w-xl mx-auto">
      <Input
        placeholder="搜索岗位、技能或关键词..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={handleFocus}
        onBlur={handleBlur}
        className="h-10"
      />
      {open && results.length > 0 && (
        <Card className="absolute z-10 mt-1 w-full max-h-80 overflow-y-auto shadow-lg">
          <ul className="divide-y">
            {results.map((r, i) => (
              <li
                key={`${r.job_title}-${r.major}-${i}`}
                className="px-4 py-3 hover:bg-muted cursor-pointer transition-colors"
                onMouseDown={(e) => {
                  // Prevent blur from firing before click
                  e.preventDefault();
                  handleSelect(r);
                }}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-sm">{r.job_title}</span>
                  <span className="text-xs text-muted-foreground shrink-0">
                    {r.major}
                  </span>
                </div>
                <div className="flex gap-1.5 mt-1.5">
                  {r.tags.slice(0, 3).map((tag) => (
                    <Badge key={tag} variant="outline" className="text-xs">
                      {tag}
                    </Badge>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
