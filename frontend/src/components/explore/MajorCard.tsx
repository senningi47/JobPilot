"use client";

import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

interface MajorCardProps {
  major: {
    major: string;
    category: string;
  };
}

export default function MajorCard({ major }: MajorCardProps) {
  return (
    <Link href={`/explore/major/${encodeURIComponent(major.major)}`}>
      <Card className="hover:shadow-md transition-shadow cursor-pointer h-full hover:ring-2 hover:ring-primary/40">
        <CardHeader>
          <CardTitle className="text-base">{major.major}</CardTitle>
        </CardHeader>
        <CardContent>
          <Badge variant="secondary">{major.category}</Badge>
        </CardContent>
      </Card>
    </Link>
  );
}
