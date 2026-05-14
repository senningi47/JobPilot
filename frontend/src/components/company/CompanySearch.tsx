"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import apiClient from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";

interface CompanySuggestion {
  name: string;
  name_en?: string;
  industry?: string;
}

export default function CompanySearch() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CompanySuggestion[]>([]);
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
          `/v1/companies/search?q=${encodeURIComponent(query.trim())}`
        );
        const data: CompanySuggestion[] = res.data ?? [];
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
    (name: string) => {
      setOpen(false);
      router.push(`/company/${encodeURIComponent(name)}`);
    },
    [router]
  );

  return (
    <div ref={containerRef} className="relative w-full max-w-xl mx-auto">
      <Input
        placeholder="搜索公司名称..."
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
                key={`${r.name}-${i}`}
                className="px-4 py-3 hover:bg-muted cursor-pointer transition-colors"
                onMouseDown={(e) => {
                  e.preventDefault();
                  handleSelect(r.name);
                }}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-sm">{r.name}</span>
                  {r.industry && (
                    <span className="text-xs text-muted-foreground shrink-0">
                      {r.industry}
                    </span>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
