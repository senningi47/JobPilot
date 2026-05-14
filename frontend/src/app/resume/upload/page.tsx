"use client";

import ResumeUpload from "@/components/resume/ResumeUpload";

export default function ResumeUploadPage() {
  return (
    <main className="container mx-auto px-4 py-10">
      <h1 className="text-2xl font-bold mb-8 text-center">上传简历</h1>
      <div className="max-w-xl mx-auto">
        <ResumeUpload />
      </div>
    </main>
  );
}
