"use client";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api";
import Link from "next/link";
function Session({ children }: { children: React.ReactNode }) {
  const session = useQuery({
    queryKey: ["session"],
    queryFn: () => api<{ id: string }>("/session", { method: "POST" }),
    staleTime: Infinity,
    retry: false,
  });
  if (session.isPending)
    return (
      <main>
        <p role="status">正在打开你的创作空间…</p>
      </main>
    );
  if (session.isError)
    return (
      <main>
        <h1>暂时无法连接工作室</h1>
        <p role="alert">{session.error.message}</p>
        <button onClick={() => session.refetch()}>重新连接</button>
      </main>
    );
  return (
    <>
      <header>
        <Link className="brand" href="/">
          ◈ 留影 <span>SHADOW STUDIO</span>
        </Link>
        <nav aria-label="主导航">
          <Link href="/">发现模板</Link>
          <Link href="/history">我的作品</Link>
        </nav>
        <span className="identity">创作者 · {session.data.id.slice(0, 6)}</span>
      </header>
      {children}
      <footer>
        留住片刻，让灵感成形。
        <span>本工作室使用可控模拟生成，无真实 AI 服务。</span>
      </footer>
    </>
  );
}
export default function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: false, refetchOnWindowFocus: false },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <Session>{children}</Session>
    </QueryClientProvider>
  );
}
