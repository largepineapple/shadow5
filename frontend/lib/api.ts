export type Template = {
  id: string;
  title: string;
  category: string;
  description: string;
  coverUrl: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};
export type Task = {
  id: string;
  templateId: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  stage: string | null;
  failedStage: string | null;
  error: string | null;
  resultUrl: string | null;
  createdAt: string;
};
export type Asset = { id: string; previewUrl: string };
export const stages: Record<string, string> = {
  ASSET_CHECK: "素材检查",
  GENERATE: "模拟生成",
  FINALIZE: "结果整理",
};
export const statuses: Record<string, string> = {
  QUEUED: "等待处理",
  RUNNING: "制作中",
  SUCCEEDED: "制作完成",
  FAILED: "制作失败",
};
export const active = (t?: Task) =>
  !!t && (t.status === "QUEUED" || t.status === "RUNNING");
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    credentials: "same-origin",
    headers:
      options.body instanceof FormData
        ? options.headers
        : { "Content-Type": "application/json", ...options.headers },
  });
  if (!response.ok) {
    const error = await response
      .json()
      .catch(() => ({ message: "网络请求失败，请重试" }));
    throw new Error(error.message || `请求失败 (${response.status})`);
  }
  return response.json();
}
