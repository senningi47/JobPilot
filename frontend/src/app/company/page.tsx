"use client";

import CompanySearch from "@/components/company/CompanySearch";

export default function CompanyPage() {
  return (
    <main className="container mx-auto px-4 py-10">
      <h1 className="text-2xl font-bold mb-8 text-center">公司情报</h1>
      <CompanySearch />
    </main>
  );
}
