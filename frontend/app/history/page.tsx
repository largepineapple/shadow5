"use client";
import { useQuery } from "@tanstack/react-query";
import { api, Task } from "@/lib/api";
import { TaskCard } from "@/components/task-card";
import { useState } from "react";
import Link from "next/link";
export default function History() {
  const [page, setPage] = useState(0);
  const user =
    useQuery<{ id: string }>({ queryKey: ["session"] }).data?.id || "";
  const query = useQuery({
    queryKey: ["history", user, page],
    queryFn: ({ signal }) =>
      api<Task[]>(`/tasks?offset=${page * 12}&limit=12`, { signal }),
  });
  return (
    <main>
      <p className="eyebrow">每一次灵感，都有迹可循</p>
      <div className="section-heading">
        <h1>我的作品</h1>
        <Link className="button" href="/">
          开始新创作 ↗
        </Link>
      </div>
      <p className="muted">任务在后台继续制作，刷新后依然在这里。</p>
      {query.isPending && <p role="status">正在加载作品…</p>}
      {query.isError && (
        <div role="alert">
          {query.error.message}
          <button onClick={() => query.refetch()}>重试</button>
        </div>
      )}
      {query.data?.length === 0 && (
        <div className="empty">
          <h2>第一份作品，等你开始</h2>
          <Link href="/">去挑选一个模板 →</Link>
        </div>
      )}
      <div className="task-grid">
        {query.data?.map((t) => (
          <TaskCard key={t.id} initial={t} user={user} />
        ))}
      </div>
      <div className="pagination">
        <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          上一页
        </button>
        <span>第 {page + 1} 页</span>
        <button
          disabled={!query.data || query.data.length < 12}
          onClick={() => setPage((p) => p + 1)}
        >
          下一页
        </button>
      </div>
    </main>
  );
}
