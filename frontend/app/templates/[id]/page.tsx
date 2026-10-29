"use client";
import { use, useState, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { api, Asset, Template, Task, stages } from "@/lib/api";
import { TaskCard } from "@/components/task-card";
import { Upload } from "@/components/upload";
export default function Detail({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  return <Editor key={id} id={id} />;
}
function Editor({ id }: { id: string }) {
  const query = useQuery({
    queryKey: ["template", id],
    queryFn: ({ signal }) => api<Template>(`/templates/${id}`, { signal }),
  });
  const [assets, setAssets] = useState<Record<number, Asset | null>>({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [tasks, setTasks] = useState<Task[]>([]);
  const [failAt, setFailAt] = useState("");
  const [delay, setDelay] = useState(2500);
  const client = useQueryClient();
  const user = client.getQueryData<{ id: string }>(["session"])?.id || "";
  const lock = useRef(false);
  const t = query.data;
  const history = useQuery({
    queryKey: ["history", user, "template", id],
    queryFn: ({ signal }) => api<Task[]>("/tasks?limit=100", { signal }),
  });
  const visible = [...tasks, ...(history.data || [])].filter(
    (task, index, list) =>
      task.templateId === id &&
      list.findIndex((t) => t.id === task.id) === index,
  );
  async function submit() {
    if (!t || lock.current) return;
    if (
      Array.from({ length: t.requiredImageCount }, (_, i) => assets[i]).some(
        (asset) => !asset,
      )
    ) {
      setError("请先准备好全部素材");
      return;
    }
    if (
      process.env.NEXT_PUBLIC_DEMO_MODE === "true" &&
      (!Number.isInteger(delay) || delay < 0 || delay > 30000)
    ) {
      setError("每阶段耗时应为 0～30000 的整数毫秒");
      return;
    }
    lock.current = true;
    setSubmitting(true);
    setError("");
    try {
      const task = await api<Task>("/tasks", {
        method: "POST",
        body: JSON.stringify({
          templateId: id,
          assetIds: Array.from(
            { length: t.requiredImageCount },
            (_, i) => assets[i]?.id,
          ),
          ...(process.env.NEXT_PUBLIC_DEMO_MODE === "true"
            ? { failAt: failAt || null, delayMs: delay }
            : {}),
        }),
      });
      setTasks((list) => [task, ...list]);
      void client.invalidateQueries({ queryKey: ["history", user] });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      lock.current = false;
      setSubmitting(false);
    }
  }
  if (query.isPending)
    return (
      <main>
        <p role="status">正在加载模板…</p>
      </main>
    );
  if (!t)
    return (
      <main>
        <h1>无法打开模板</h1>
        <p role="alert">{query.error?.message}</p>
        <button onClick={() => query.refetch()}>重新加载</button>
        <Link href="/">返回模板列表</Link>
      </main>
    );
  return (
    <main>
      <Link className="back" href="/">
        ← 返回模板列表
      </Link>
      <div className="studio-layout">
        <div className="editor">
          <section className="example">
            <img src={t.coverUrl} alt={t.title + " 示例封面"} />
            <p>效果示意 · 实际结果由模拟生成器提供</p>
          </section>
          <section className="workbench">
            <p className="eyebrow">{t.category}</p>
            <h1>{t.title}</h1>
            <p>{t.description}</p>
            <hr />
            <h2>
              放入你的照片{" "}
              <small>
                {Object.values(assets).filter(Boolean).length} /{" "}
                {t.requiredImageCount}
              </small>
            </h2>
            <p className="muted">
              需要 {t.requiredImageCount} 张不同图片，每张不超过{" "}
              {Math.round(t.maxFileSizeBytes / 1024 / 1024)}MB。
            </p>
            <div className="uploads">
              {Array.from({ length: t.requiredImageCount }, (_, i) => (
                <Upload
                  key={i}
                  index={i}
                  acceptedMimeTypes={t.acceptedMimeTypes}
                  maxFileSizeBytes={t.maxFileSizeBytes}
                  disabled={submitting}
                  onChange={(asset) => setAssets((a) => ({ ...a, [i]: asset }))}
                />
              ))}
            </div>
            {process.env.NEXT_PUBLIC_DEMO_MODE === "true" && (
              <details>
                <summary>演示控制</summary>
                <label>
                  失败阶段
                  <select
                    value={failAt}
                    onChange={(e) => setFailAt(e.target.value)}
                  >
                    <option value="">全部成功</option>
                    {Object.entries(stages).map(([v, l]) => (
                      <option key={v} value={v}>
                        {l}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  每阶段耗时（毫秒）
                  <input
                    type="number"
                    min="0"
                    max="30000"
                    value={delay}
                    onChange={(e) => setDelay(Number(e.target.value))}
                  />
                </label>
              </details>
            )}
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
            <button
              className="primary submit"
              disabled={
                submitting ||
                Array.from(
                  { length: t.requiredImageCount },
                  (_, i) => assets[i],
                ).some((a) => !a)
              }
              onClick={submit}
            >
              {submitting ? "正在提交…" : "开始制作 ↗"}
            </button>
            <small>提交后可继续浏览，结果自动保存在「我的作品」。</small>
          </section>
        </div>
        <section className="studio-history">
          <div className="section-heading">
            <h2>制作记录</h2>
            <Link href="/history">全部作品 →</Link>
          </div>
          <div className="task-grid">
            {history.isPending && visible.length === 0 && (
              <p role="status">正在加载制作记录…</p>
            )}
            {history.isSuccess && visible.length === 0 && (
              <div className="empty">
                还没有制作记录，上传照片开始第一次创作。
              </div>
            )}
            {history.isError && (
              <div className="notice" role="alert">
                历史加载失败
                <button onClick={() => history.refetch()}>重新加载</button>
              </div>
            )}
            {visible.map((task) => (
              <TaskCard key={task.id} initial={task} user={user} />
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
