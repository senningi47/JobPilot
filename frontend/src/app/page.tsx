import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-8">
      <div className="max-w-2xl text-center">
        <h1 className="text-5xl font-bold text-primary-600 mb-4">JobPilot</h1>
        <p className="text-xl text-gray-600 mb-8">AI 智能求职助手</p>
        <p className="text-gray-500 mb-12">
          从岗位探索到拿到 Offer，让 AI 为你的求职之路保驾护航
        </p>
        <div className="flex gap-4 justify-center">
          <Link href="/explore" className={buttonVariants({ size: "lg" })}>
            开始探索
          </Link>
          <Link
            href="/login"
            className={buttonVariants({ variant: "outline", size: "lg" })}
          >
            登录
          </Link>
        </div>
      </div>
    </main>
  );
}
