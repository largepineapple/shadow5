"use client";
import { useState } from "react";
export function ResultPreview({ url }: { url: string }) {
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  const src = attempt
    ? `${url}${url.includes("?") ? "&" : "?"}retry=${attempt}`
    : url;
  return (
    <div
      className={`result-preview result-${state}`}
      aria-busy={state === "loading"}
    >
      {state === "loading" && (
        <span className="result-placeholder" role="status">
          <span className="spinner" aria-hidden="true" />
          正在加载作品…
        </span>
      )}
      {state === "error" && (
        <div className="notice" role="alert">
          结果图片暂时无法加载，制作已完成。
          <button
            onClick={() => {
              setState("loading");
              setAttempt((n) => n + 1);
            }}
          >
            重新加载图片
          </button>
        </div>
      )}
      <a href={url} target="_blank" rel="noreferrer" hidden={state === "error"}>
        <img
          key={src}
          className="result"
          src={src}
          alt="制作结果，点击打开完整图片"
          width={1200}
          height={800}
          onLoad={() => setState("ready")}
          onError={() => setState("error")}
        />
      </a>
    </div>
  );
}
