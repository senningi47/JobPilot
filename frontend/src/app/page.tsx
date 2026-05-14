export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-8">
      <div className="max-w-2xl text-center">
        <h1 className="text-4xl font-bold text-primary-600 mb-4">JobPilot</h1>
        <p className="text-xl text-gray-600 mb-8">AI 智能求职助手</p>
        <p className="text-gray-500 mb-12">
          从岗位探索到拿到 Offer，让 AI 为你的求职之路保驾护航
        </p>
        <div className="flex gap-4 justify-center">
          <button className="px-6 py-3 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors">
            开始探索
          </button>
          <button className="px-6 py-3 border border-primary-600 text-primary-600 rounded-lg hover:bg-primary-50 transition-colors">
            上传简历
          </button>
        </div>
      </div>
    </main>
  );
}
