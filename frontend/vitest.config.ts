import { defineConfig } from "vitest/config";
export default defineConfig({
  test: { environment: "jsdom", setupFiles: ["./test-setup.ts"] },
  resolve: { alias: { "@": new URL(".", import.meta.url).pathname } },
});
