"use client";
import { useEffect, useRef, useState } from "react";
import { api, Asset } from "@/lib/api";

type UploadState = {
  status: "empty" | "uploading" | "ready" | "error";
  preview?: string;
  asset?: Asset;
  error?: string;
};
export function Upload({
  index,
  acceptedMimeTypes,
  maxFileSizeBytes,
  disabled = false,
  onChange,
}: {
  index: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
  disabled?: boolean;
  onChange: (asset: Asset | null) => void;
}) {
  const [state, setState] = useState<UploadState>({ status: "empty" });
  const request = useRef<AbortController | null>(null);
  const preview = useRef<string | null>(null);
  const types = acceptedMimeTypes
    .map((type) => type.replace("image/", "").toUpperCase())
    .join(" / ");
  const size = `${Math.round(maxFileSizeBytes / 1024 / 1024)}MB`;
  useEffect(
    () => () => {
      request.current?.abort();
      if (preview.current) URL.revokeObjectURL(preview.current);
    },
    [],
  );
  async function choose(file?: File) {
    request.current?.abort();
    onChange(null);
    if (preview.current) URL.revokeObjectURL(preview.current);
    preview.current = null;
    if (!file) {
      setState({ status: "empty" });
      return;
    }
    if (
      !acceptedMimeTypes.includes(file.type) ||
      file.size === 0 ||
      file.size > maxFileSizeBytes
    ) {
      setState({
        status: "error",
        error: `请选择不超过 ${size} 的有效 ${types} 图片`,
      });
      return;
    }
    const url = URL.createObjectURL(file);
    preview.current = url;
    const controller = new AbortController();
    request.current = controller;
    setState({ status: "uploading", preview: url });
    const body = new FormData();
    body.append("file", file);
    try {
      const asset = await api<Asset>("/assets", {
        method: "POST",
        body,
        signal: controller.signal,
      });
      if (!controller.signal.aborted) {
        setState({ status: "ready", preview: url, asset });
        onChange(asset);
      }
    } catch (error) {
      if (!controller.signal.aborted)
        setState({
          status: "error",
          preview: url,
          error: error instanceof Error ? error.message : "上传失败，请重试",
        });
    }
  }
  return (
    <div
      className={`upload-slot upload-${state.status}`}
      aria-busy={state.status === "uploading"}
    >
      {state.preview ? (
        <div className="upload-preview">
          <img src={state.preview} alt={`素材 ${index + 1} 预览`} />
          {state.status === "uploading" && (
            <span className="upload-overlay">
              <span className="spinner" aria-hidden="true" />
            </span>
          )}
        </div>
      ) : (
        <span className="upload-icon" aria-hidden="true">
          ＋
        </span>
      )}
      <label className="upload-label">
        {state.asset ? "替换图片" : `选择图片 ${index + 1}`}
        <input
          disabled={disabled}
          aria-label={`上传或替换图片 ${index + 1}`}
          type="file"
          accept={acceptedMimeTypes.join(",")}
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void choose(file);
            event.target.value = "";
          }}
        />
      </label>
      <small role="status">
        {state.status === "uploading"
          ? "正在上传…"
          : state.status === "ready"
            ? "✓ 已上传"
            : `${types} · 最大 ${size}`}
      </small>
      {state.preview && (
        <button
          disabled={disabled}
          className="text-button"
          onClick={() => choose()}
        >
          移除图片 {index + 1}
        </button>
      )}
      {state.error && (
        <p className="error" role="alert">
          {state.error}，请重新选择。
        </p>
      )}
    </div>
  );
}
