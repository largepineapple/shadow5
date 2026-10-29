"use client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import Link from "next/link";
import { api, Template } from "@/lib/api";
export default function Home() {
  const [category, setCategory] = useState("全部模板");
  const query = useQuery({
    queryKey: ["templates"],
    queryFn: ({ signal }) => api<Template[]>("/templates", { signal }),
  });
  const categories = [
    "全部模板",
    ...new Set(query.data?.map((t) => t.category)),
  ];
  return (
    <main>
      <section className="hero">
        <div>
          <p className="eyebrow">灵感，从一张照片开始</p>
          <h1>
            让平凡瞬间，
            <br />
            有自己的<span>表达。</span>
          </h1>
          <p>
            挑选一份灵感，放入你的照片。
            <br />
            制作交给工作室，你可以继续探索。
          </p>
          <Link className="button" href="#templates">
            探索模板 ↗
          </Link>
        </div>
        <div className="hero-art">
          <img src="/demo/postcard.svg" alt="旅行明信片示例" />
          <span className="art-note">把旅行，寄给明天</span>
        </div>
      </section>
      <section id="templates">
        <div className="section-heading">
          <div>
            <p className="eyebrow">为你的下一次创作</p>
            <h2>发现模板</h2>
          </div>
          <span>{query.data?.length || 0} 份灵感 · 持续收藏</span>
        </div>
        <div className="tabs" aria-label="模板分类">
          {categories.map((c) => (
            <button
              aria-pressed={c === category}
              key={c}
              onClick={() => setCategory(c)}
            >
              {c}
            </button>
          ))}
        </div>
        {query.isPending && <p role="status">正在寻找灵感…</p>}
        {query.isError && (
          <div role="alert">
            <p>{query.error.message}</p>
            <button onClick={() => query.refetch()}>重新加载</button>
          </div>
        )}
        <div className="grid">
          {query.data
            ?.filter((t) => category === "全部模板" || t.category === category)
            .map((t) => (
              <Link
                className="template-card"
                href={`/templates/${t.id}`}
                key={t.id}
              >
                <div className="cover">
                  <img src={t.coverUrl} alt={t.title} />
                  <span>{t.requiredImageCount} 张照片</span>
                </div>
                <div className="card-copy">
                  <small>{t.category}</small>
                  <h3>
                    {t.title}
                    <span>↗</span>
                  </h3>
                  <p>{t.description}</p>
                </div>
              </Link>
            ))}
        </div>
        {query.data?.length === 0 && <p>暂时没有模板，请稍后再来。</p>}
      </section>
    </main>
  );
}
