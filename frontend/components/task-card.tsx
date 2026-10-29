"use client";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { active, api, Task, statuses, stages } from "@/lib/api";
export function TaskCard({ initial, user }: { initial: Task; user: string }) {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ["task", user, initial.id],
    queryFn: ({ signal }) =>
      api<{ task: Task; steps: { stage: string; status: string }[] }>(
        `/tasks/${initial.id}`,
        { signal },
      ),
    initialData: { task: initial, steps: [] },
    refetchInterval: (q) =>
      q.state.error ? false : active(q.state.data?.task) ? 2000 : false,
  });
  const task = query.data.task;
  return (
    <article className={`task-card ${task.status.toLowerCase()}`}>
      <div className="task-heading">
        <span className="status">{statuses[task.status]}</span>
        <time>
          {new Date(
            task.createdAt + (/Z$/.test(task.createdAt) ? "" : "Z"),
          ).toLocaleString("zh-CN")}
        </time>
      </div>
      <h3>创作 · {task.templateId}</h3>
      <small className="task-id">{task.id}</small>
      <ol className="steps">
        {Object.entries(stages).map(([stage, label]) => (
          <li key={stage} className={task.stage === stage ? "current" : ""}>
            {label}
            {query.data.steps.find((s) => s.stage === stage)?.status ===
            "SUCCEEDED"
              ? " ✓"
              : ""}
          </li>
        ))}
      </ol>
      <div aria-live="polite">
        {active(task) && (
          <p>
            {task.status === "QUEUED"
              ? "已加入队列，你可以继续制作其他作品。"
              : `正在${stages[task.stage || ""] || "处理"}，离开页面也会继续制作。`}
          </p>
        )}
        {task.status === "FAILED" && (
          <p className="error">
            {stages[task.failedStage || ""]}：{task.error}
          </p>
        )}
      </div>
      {query.isError && (
        <div className="notice" role="alert">
          状态暂时无法更新，已保留上次记录。
          <button onClick={() => query.refetch()}>恢复查询</button>
        </div>
      )}
      {task.resultUrl && (
        <a href={task.resultUrl} target="_blank" rel="noreferrer">
          <img
            className="result"
            src={task.resultUrl}
            alt="制作结果，点击打开完整图片"
          />
        </a>
      )}
      <button
        className="text-button"
        onClick={() =>
          client.invalidateQueries({ queryKey: ["task", user, task.id] })
        }
      >
        更新状态
      </button>
    </article>
  );
}
