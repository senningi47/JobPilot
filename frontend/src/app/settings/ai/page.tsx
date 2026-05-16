"use client";

import { useEffect, useState, useCallback } from "react";
import apiClient from "@/lib/api";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";

interface AiProvider {
  id: number;
  providerName: string;
  displayName: string;
  baseUrl: string;
  apiKey: string;
  modelName: string;
  isActive: boolean;
  isBuiltin: boolean;
}

interface SearchProvider {
  id: number;
  providerName: string;
  displayName: string;
  apiKey: string;
  baseUrl: string;
  isActive: boolean;
  isBuiltin: boolean;
}

export default function AiSettingsPage() {
  return (
    <main className="container mx-auto max-w-4xl px-4 py-10">
      <h1 className="text-2xl font-bold mb-6">AI 设置</h1>
      <Tabs defaultValue="ai">
        <TabsList variant="line">
          <TabsTrigger value="ai">AI 模型</TabsTrigger>
          <TabsTrigger value="search">联网搜索</TabsTrigger>
        </TabsList>
        <TabsContent value="ai">
          <AiProviderTab />
        </TabsContent>
        <TabsContent value="search">
          <SearchProviderTab />
        </TabsContent>
      </Tabs>
    </main>
  );
}

/* ──────────────── AI Provider Tab ──────────────── */

function AiProviderTab() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingKey, setEditingKey] = useState<Record<number, string>>({});
  const [editingModel, setEditingModel] = useState<Record<number, string>>({});
  const [testing, setTesting] = useState<Record<number, boolean>>({});
  const [testResult, setTestResult] = useState<Record<number, { ok: boolean; msg: string }>>({});
  const [customForm, setCustomForm] = useState({ displayName: "", baseUrl: "", apiKey: "", modelName: "" });

  const fetchProviders = useCallback(async () => {
    try {
      setError(null);
      const res: any = await apiClient.get("/v1/ai/providers");
      setProviders(res.data ?? []);
    } catch (e: any) {
      console.error("Failed to fetch AI providers:", e);
      setError(e?.message || "加载失败，请检查后端服务是否运行");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchProviders(); }, [fetchProviders]);

  const handleSaveKey = async (id: number) => {
    const key = editingKey[id];
    if (!key) return;
    try {
      await apiClient.put(`/v1/ai/providers/${id}`, { apiKey: key });
      setEditingKey((prev) => { const next = { ...prev }; delete next[id]; return next; });
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleSaveModel = async (id: number) => {
    const model = editingModel[id];
    if (!model) return;
    try {
      await apiClient.put(`/v1/ai/providers/${id}`, { modelName: model });
      setEditingModel((prev) => { const next = { ...prev }; delete next[id]; return next; });
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleActivate = async (id: number) => {
    try {
      await apiClient.put(`/v1/ai/providers/${id}/activate`);
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleTest = async (id: number) => {
    setTesting((p) => ({ ...p, [id]: true }));
    setTestResult((p) => { const n = { ...p }; delete n[id]; return n; });
    try {
      const res: any = await apiClient.get(`/v1/ai/providers/${id}/test`);
      const d = res.data;
      setTestResult((p) => ({
        ...p,
        [id]: d.success
          ? { ok: true, msg: `连接成功 (${d.latencyMs}ms)` }
          : { ok: false, msg: d.error || "连接失败" },
      }));
    } catch (e: any) {
      setTestResult((p) => ({ ...p, [id]: { ok: false, msg: e.message || "请求失败" } }));
    }
    finally { setTesting((p) => ({ ...p, [id]: false })); }
  };

  const handleCreateCustom = async () => {
    if (!customForm.baseUrl || !customForm.modelName) return;
    try {
      await apiClient.post("/v1/ai/providers", customForm);
      setCustomForm({ displayName: "", baseUrl: "", apiKey: "", modelName: "" });
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleDelete = async (id: number) => {
    try {
      await apiClient.delete(`/v1/ai/providers/${id}`);
      fetchProviders();
    } catch { /* ignore */ }
  };

  if (loading) {
    return <div className="grid gap-4 mt-4">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-xl" />)}</div>;
  }

  if (error) {
    return (
      <div className="mt-4 p-6 border border-destructive/50 rounded-lg bg-destructive/5 text-center">
        <p className="text-sm text-destructive mb-3">{error}</p>
        <Button variant="outline" size="sm" onClick={fetchProviders}>重试</Button>
      </div>
    );
  }

  return (
    <div className="grid gap-4 mt-4">
      {providers.filter((p) => p.isBuiltin).map((p) => (
        <Card key={p.id} className={p.isActive ? "ring-2 ring-green-500" : ""}>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {p.displayName}
                {p.isActive && <span className="text-xs bg-green-500 text-white px-2 py-0.5 rounded-full">已激活</span>}
              </CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap items-end gap-3 mb-3">
              <div className="flex-1 min-w-[200px]">
                <label className="text-xs text-muted-foreground mb-1 block">模型名</label>
                <Input
                  placeholder={p.modelName || "输入模型名"}
                  value={editingModel[p.id] ?? ""}
                  onChange={(e) => setEditingModel((prev) => ({ ...prev, [p.id]: e.target.value }))}
                />
              </div>
              <Button variant="outline" size="sm" onClick={() => handleSaveModel(p.id)} disabled={!editingModel[p.id]}>保存模型</Button>
            </div>
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex-1 min-w-[200px]">
                <label className="text-xs text-muted-foreground mb-1 block">API Key</label>
                <Input
                  type="password"
                  placeholder={p.apiKey && p.apiKey.startsWith("****") ? p.apiKey : "填入 API Key"}
                  value={editingKey[p.id] ?? ""}
                  onChange={(e) => setEditingKey((prev) => ({ ...prev, [p.id]: e.target.value }))}
                />
              </div>
              <Button variant="outline" size="sm" onClick={() => handleSaveKey(p.id)} disabled={!editingKey[p.id]}>保存 Key</Button>
              <Button variant={p.isActive ? "secondary" : "default"} size="sm" onClick={() => handleActivate(p.id)} disabled={p.isActive}>
                {p.isActive ? "当前使用中" : "设为当前"}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => handleTest(p.id)} disabled={testing[p.id]}>
                {testing[p.id] ? "测试中..." : "测试连接"}
              </Button>
            </div>
            {testResult[p.id] && (
              <p className={`text-xs mt-2 ${testResult[p.id].ok ? "text-green-600" : "text-red-500"}`}>
                {testResult[p.id].msg}
              </p>
            )}
          </CardContent>
        </Card>
      ))}

      {/* Custom provider form */}
      <Card>
        <CardHeader>
          <CardTitle>自定义 Provider</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">名称</label>
                <Input placeholder="我的 DeepSeek" value={customForm.displayName} onChange={(e) => setCustomForm((p) => ({ ...p, displayName: e.target.value }))} />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">模型名</label>
                <Input placeholder="deepseek-chat" value={customForm.modelName} onChange={(e) => setCustomForm((p) => ({ ...p, modelName: e.target.value }))} />
              </div>
            </div>
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">Base URL</label>
              <Input placeholder="https://api.example.com/v1" value={customForm.baseUrl} onChange={(e) => setCustomForm((p) => ({ ...p, baseUrl: e.target.value }))} />
            </div>
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">API Key</label>
              <Input type="password" placeholder="sk-..." value={customForm.apiKey} onChange={(e) => setCustomForm((p) => ({ ...p, apiKey: e.target.value }))} />
            </div>
            <Button size="sm" onClick={handleCreateCustom} disabled={!customForm.baseUrl || !customForm.modelName}>添加自定义 Provider</Button>
          </div>
        </CardContent>
      </Card>

      {/* List custom providers */}
      {providers.filter((p) => !p.isBuiltin).map((p) => (
        <Card key={p.id} className={p.isActive ? "ring-2 ring-green-500" : ""}>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {p.displayName}
                {p.isActive && <span className="text-xs bg-green-500 text-white px-2 py-0.5 rounded-full">已激活</span>}
              </CardTitle>
              <Button variant="destructive" size="xs" onClick={() => handleDelete(p.id)}>删除</Button>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground mb-2">{p.baseUrl}</p>
            <div className="flex flex-wrap items-end gap-3 mb-3">
              <div className="flex-1 min-w-[200px]">
                <label className="text-xs text-muted-foreground mb-1 block">模型名</label>
                <Input
                  placeholder={p.modelName || "输入模型名"}
                  value={editingModel[p.id] ?? ""}
                  onChange={(e) => setEditingModel((prev) => ({ ...prev, [p.id]: e.target.value }))}
                />
              </div>
              <Button variant="outline" size="sm" onClick={() => handleSaveModel(p.id)} disabled={!editingModel[p.id]}>保存模型</Button>
            </div>
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex-1 min-w-[200px]">
                <label className="text-xs text-muted-foreground mb-1 block">API Key</label>
                <Input
                  type="password"
                  placeholder={p.apiKey && p.apiKey.startsWith("****") ? p.apiKey : "填入 API Key"}
                  value={editingKey[p.id] ?? ""}
                  onChange={(e) => setEditingKey((prev) => ({ ...prev, [p.id]: e.target.value }))}
                />
              </div>
              <Button variant="outline" size="sm" onClick={() => handleSaveKey(p.id)} disabled={!editingKey[p.id]}>保存 Key</Button>
              <Button variant={p.isActive ? "secondary" : "default"} size="sm" onClick={() => handleActivate(p.id)} disabled={p.isActive}>
                {p.isActive ? "当前使用中" : "设为当前"}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => handleTest(p.id)} disabled={testing[p.id]}>
                {testing[p.id] ? "测试中..." : "测试连接"}
              </Button>
            </div>
            {testResult[p.id] && (
              <p className={`text-xs mt-2 ${testResult[p.id].ok ? "text-green-600" : "text-red-500"}`}>
                {testResult[p.id].msg}
              </p>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

/* ──────────────── Search Provider Tab ──────────────── */

function SearchProviderTab() {
  const [providers, setProviders] = useState<SearchProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingKey, setEditingKey] = useState<Record<number, string>>({});
  const [editingUrl, setEditingUrl] = useState<Record<number, string>>({});
  const [testing, setTesting] = useState<Record<number, boolean>>({});
  const [testResult, setTestResult] = useState<Record<number, { ok: boolean; msg: string }>>({});
  const [customForm, setCustomForm] = useState({ providerName: "custom", displayName: "", baseUrl: "", apiKey: "" });

  const fetchProviders = useCallback(async () => {
    try {
      setError(null);
      const res: any = await apiClient.get("/v1/search/providers");
      setProviders(res.data ?? []);
    } catch (e: any) {
      console.error("Failed to fetch search providers:", e);
      setError(e?.message || "加载失败，请检查后端服务是否运行");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchProviders(); }, [fetchProviders]);

  const handleSave = async (id: number) => {
    const body: Record<string, string> = {};
    if (editingKey[id]) body.apiKey = editingKey[id];
    if (editingUrl[id]) body.baseUrl = editingUrl[id];
    if (Object.keys(body).length === 0) return;
    try {
      await apiClient.put(`/v1/search/providers/${id}`, body);
      setEditingKey((prev) => { const n = { ...prev }; delete n[id]; return n; });
      setEditingUrl((prev) => { const n = { ...prev }; delete n[id]; return n; });
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleActivate = async (id: number) => {
    try {
      await apiClient.put(`/v1/search/providers/${id}/activate`);
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleTest = async (id: number) => {
    setTesting((p) => ({ ...p, [id]: true }));
    setTestResult((p) => { const n = { ...p }; delete n[id]; return n; });
    try {
      const res: any = await apiClient.get(`/v1/search/providers/${id}/test`);
      const d = res.data;
      setTestResult((p) => ({
        ...p,
        [id]: d.success
          ? { ok: true, msg: `连接成功 (${d.latencyMs}ms)` }
          : { ok: false, msg: d.error || "连接失败" },
      }));
    } catch (e: any) {
      setTestResult((p) => ({ ...p, [id]: { ok: false, msg: e.message || "请求失败" } }));
    }
    finally { setTesting((p) => ({ ...p, [id]: false })); }
  };

  const handleCreateCustom = async () => {
    if (!customForm.displayName) return;
    try {
      await apiClient.post("/v1/search/providers", customForm);
      setCustomForm({ providerName: "custom", displayName: "", baseUrl: "", apiKey: "" });
      fetchProviders();
    } catch { /* ignore */ }
  };

  const handleDelete = async (id: number) => {
    try {
      await apiClient.delete(`/v1/search/providers/${id}`);
      fetchProviders();
    } catch { /* ignore */ }
  };

  const providerHints: Record<string, string> = {
    tavily: "tavily.com 注册获取，1000次/月免费",
    serper: "serper.dev 注册获取，2500次免费",
    searxng: "无需 Key，填入实例 URL 即可",
    bing: "Azure 账号申请 Bing Search API",
  };

  if (loading) {
    return <div className="grid gap-4 mt-4">{Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-xl" />)}</div>;
  }

  if (error) {
    return (
      <div className="mt-4 p-6 border border-destructive/50 rounded-lg bg-destructive/5 text-center">
        <p className="text-sm text-destructive mb-3">{error}</p>
        <Button variant="outline" size="sm" onClick={fetchProviders}>重试</Button>
      </div>
    );
  }

  return (
    <div className="grid gap-4 mt-4">
      {providers.filter((p) => p.isBuiltin).map((p) => (
        <Card key={p.id} className={p.isActive ? "ring-2 ring-green-500" : ""}>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {p.displayName}
                {p.isActive && <span className="text-xs bg-green-500 text-white px-2 py-0.5 rounded-full">已激活</span>}
              </CardTitle>
              <span className="text-xs text-muted-foreground">{providerHints[p.providerName] || ""}</span>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap items-end gap-3">
              {p.providerName === "searxng" ? (
                <div className="flex-1 min-w-[200px]">
                  <label className="text-xs text-muted-foreground mb-1 block">实例 URL</label>
                  <Input
                    placeholder={p.baseUrl || "https://searx.be"}
                    value={editingUrl[p.id] ?? ""}
                    onChange={(e) => setEditingUrl((prev) => ({ ...prev, [p.id]: e.target.value }))}
                  />
                </div>
              ) : (
                <div className="flex-1 min-w-[200px]">
                  <label className="text-xs text-muted-foreground mb-1 block">API Key</label>
                  <Input
                    type="password"
                    placeholder={p.apiKey && p.apiKey.startsWith("****") ? p.apiKey : "填入 API Key"}
                    value={editingKey[p.id] ?? ""}
                    onChange={(e) => setEditingKey((prev) => ({ ...prev, [p.id]: e.target.value }))}
                  />
                </div>
              )}
              <Button variant="outline" size="sm" onClick={() => handleSave(p.id)} disabled={!editingKey[p.id] && !editingUrl[p.id]}>保存</Button>
              <Button variant={p.isActive ? "secondary" : "default"} size="sm" onClick={() => handleActivate(p.id)} disabled={p.isActive}>
                {p.isActive ? "当前使用中" : "设为当前"}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => handleTest(p.id)} disabled={testing[p.id]}>
                {testing[p.id] ? "测试中..." : "测试搜索"}
              </Button>
            </div>
            {testResult[p.id] && (
              <p className={`text-xs mt-2 ${testResult[p.id].ok ? "text-green-600" : "text-red-500"}`}>
                {testResult[p.id].msg}
              </p>
            )}
          </CardContent>
        </Card>
      ))}

      {/* Custom search provider form */}
      <Card>
        <CardHeader>
          <CardTitle>自定义搜索源</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">名称</label>
                <Input placeholder="我的搜索源" value={customForm.displayName} onChange={(e) => setCustomForm((p) => ({ ...p, displayName: e.target.value }))} />
              </div>
              <div>
                <label className="text-xs text-muted-foreground mb-1 block">Base URL</label>
                <Input placeholder="https://..." value={customForm.baseUrl} onChange={(e) => setCustomForm((p) => ({ ...p, baseUrl: e.target.value }))} />
              </div>
            </div>
            <div>
              <label className="text-xs text-muted-foreground mb-1 block">API Key</label>
              <Input type="password" placeholder="可选" value={customForm.apiKey} onChange={(e) => setCustomForm((p) => ({ ...p, apiKey: e.target.value }))} />
            </div>
            <Button size="sm" onClick={handleCreateCustom} disabled={!customForm.displayName}>添加自定义搜索源</Button>
          </div>
        </CardContent>
      </Card>

      {/* List custom search providers */}
      {providers.filter((p) => !p.isBuiltin).map((p) => (
        <Card key={p.id} className={p.isActive ? "ring-2 ring-green-500" : ""}>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                {p.displayName}
                {p.isActive && <span className="text-xs bg-green-500 text-white px-2 py-0.5 rounded-full">已激活</span>}
              </CardTitle>
              <Button variant="destructive" size="xs" onClick={() => handleDelete(p.id)}>删除</Button>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground mb-2">{p.baseUrl || "无 Base URL"}</p>
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex-1 min-w-[200px]">
                <label className="text-xs text-muted-foreground mb-1 block">API Key</label>
                <Input
                  type="password"
                  placeholder={p.apiKey && p.apiKey.startsWith("****") ? p.apiKey : "填入 API Key"}
                  value={editingKey[p.id] ?? ""}
                  onChange={(e) => setEditingKey((prev) => ({ ...prev, [p.id]: e.target.value }))}
                />
              </div>
              <Button variant="outline" size="sm" onClick={() => handleSave(p.id)} disabled={!editingKey[p.id]}>保存</Button>
              <Button variant={p.isActive ? "secondary" : "default"} size="sm" onClick={() => handleActivate(p.id)} disabled={p.isActive}>
                {p.isActive ? "当前使用中" : "设为当前"}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => handleTest(p.id)} disabled={testing[p.id]}>
                {testing[p.id] ? "测试中..." : "测试搜索"}
              </Button>
            </div>
            {testResult[p.id] && (
              <p className={`text-xs mt-2 ${testResult[p.id].ok ? "text-green-600" : "text-red-500"}`}>
                {testResult[p.id].msg}
              </p>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
