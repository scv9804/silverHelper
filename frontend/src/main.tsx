import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./auth";

async function start() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === "true") {
    const { worker } = await import("./mock");
    await worker.start();
  }
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider><BrowserRouter><App /></BrowserRouter></AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
}
start();
