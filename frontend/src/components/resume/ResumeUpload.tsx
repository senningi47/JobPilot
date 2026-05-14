"use client";

import { useCallback, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import apiClient from "@/lib/api";
import { cn } from "@/lib/utils";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Upload } from "lucide-react";

const ACCEPTED = [".pdf", ".docx"];

export default function ResumeUpload() {
  const router = useRouter();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isValidFile = useCallback((f: File) => {
    const name = f.name.toLowerCase();
    return ACCEPTED.some((ext) => name.endsWith(ext));
  }, []);

  const handleFiles = useCallback(
    (files: FileList | null) => {
      setError(null);
      if (!files || files.length === 0) return;
      const f = files[0];
      if (!isValidFile(f)) {
        setError("仅支持 .pdf 和 .docx 格式");
        return;
      }
      setFile(f);
    },
    [isValidFile]
  );

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    setError(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const res: any = await apiClient.post("/v1/resumes/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      const id = res.data?.id;
      if (id) {
        router.push(`/resume/${id}`);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || "上传失败，请重试");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="space-y-4">
      <Card
        className={cn(
          "flex flex-col items-center justify-center gap-4 border-dashed border-2 p-10 cursor-pointer transition-colors",
          dragOver
            ? "border-primary bg-primary/5"
            : "border-muted-foreground/25 hover:border-muted-foreground/50"
        )}
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          handleFiles(e.dataTransfer.files);
        }}
        onClick={() => inputRef.current?.click()}
      >
        <Upload className="h-8 w-8 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">
          拖拽文件到此处，或<span className="text-primary font-medium">点击选择</span>
        </p>
        <p className="text-xs text-muted-foreground">支持 .pdf、.docx 格式</p>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.docx"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
      </Card>

      {file && (
        <div className="flex items-center justify-between gap-3 px-1">
          <span className="text-sm truncate max-w-xs">{file.name}</span>
          <Button size="sm" onClick={handleUpload} disabled={uploading}>
            {uploading ? "上传中..." : "上传"}
          </Button>
        </div>
      )}

      {error && (
        <p className="text-sm text-destructive px-1">{error}</p>
      )}
    </div>
  );
}
