import React from "react";
import { describe, it, expect, vi } from "vitest";
import {
  render,
  screen,
  waitFor,
  fireEvent,
  cleanup,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TaskCard } from "./task-card";
import type { Task } from "../lib/api";
function task(id: string): Task {
  return {
    id,
    templateId: "postcard",
    status: "RUNNING",
    stage: "GENERATE",
    failedStage: null,
    error: null,
    resultUrl: null,
    createdAt: "2026-10-29T10:00:00",
  };
}
function mount(tasks: Task[]) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  render(
    <QueryClientProvider client={client}>
      {tasks.map((t) => (
        <TaskCard key={t.id} initial={t} user="user" />
      ))}
    </QueryClientProvider>,
  );
  return client;
}
describe("任务隔离与请求异常", () => {
  it("一个任务失败不改变另一个任务", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: string) => ({
        ok: true,
        json: async () => ({
          task: input.endsWith("/a")
            ? {
                ...task("a"),
                status: "FAILED",
                failedStage: "GENERATE",
                error: "模拟生成失败",
              }
            : { ...task("b"), status: "SUCCEEDED" },
          steps: [],
        }),
      })),
    );
    const client = mount([task("a"), task("b")]);
    await waitFor(() =>
      expect(screen.getByText("制作失败")).toBeInTheDocument(),
    );
    expect(await screen.findByText("制作完成")).toBeInTheDocument();
    expect(screen.getByText(/模拟生成失败/)).toBeInTheDocument();
    client.clear();
  });
  it("查询异常保留处理中，恢复查询后才显示真实业务失败", async () => {
    const fetch = vi
      .fn()
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValue({
        ok: true,
        json: async () => ({
          task: {
            ...task("a"),
            status: "FAILED",
            failedStage: "GENERATE",
            error: "后台失败",
          },
          steps: [],
        }),
      });
    vi.stubGlobal("fetch", fetch);
    const client = mount([task("a")]);
    await screen.findByText(/状态暂时无法更新/);
    expect(screen.getByText("制作中")).toBeInTheDocument();
    expect(screen.queryByText("制作失败")).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("恢复查询"));
    await screen.findByText("制作失败");
    client.clear();
  });
});

it("终态停止自动查询", async () => {
  const fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({
      task: { ...task("done"), status: "SUCCEEDED" },
      steps: [],
    }),
  });
  vi.stubGlobal("fetch", fetch);
  const client = mount([task("done")]);
  await screen.findByText("制作完成");
  const calls = fetch.mock.calls.length;
  await new Promise((resolve) => setTimeout(resolve, 2200));
  expect(fetch).toHaveBeenCalledTimes(calls);
  client.clear();
});

it("离开页面会取消在途查询且不再轮询", async () => {
  let observedSignal: AbortSignal | undefined;
  const fetch = vi.fn((_url: string, options: RequestInit) => {
    observedSignal = options.signal as AbortSignal;
    return new Promise(() => {});
  });
  vi.stubGlobal("fetch", fetch);
  const client = mount([task("pending")]);
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  cleanup();
  expect(observedSignal?.aborted).toBe(true);
  await new Promise((resolve) => setTimeout(resolve, 2200));
  expect(fetch).toHaveBeenCalledTimes(1);
  client.clear();
});
