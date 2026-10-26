import type { Metadata } from "next";
import { headers } from 'next/headers';
import "./globals.css";
import "md-editor-rt/lib/style.css";
import "katex/dist/katex.min.css";
import GlobalFerrofluidBackground from "./components/GlobalFerrofluidBackground";
import GlobalRouteLoader from "./components/GlobalRouteLoader";
import SiteFooter from "./components/SiteFooter";
import ClientRuntime from "./components/ClientRuntime";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.PUBLIC_SITE_URL || "http://localhost:3403"),
  title: {
    default: "PAA 竞赛管理平台",
    template: "%s",
  },
  description: "PAA 竞赛管理平台",
  keywords: ["竞赛管理平台", "程序设计", "PAA", "算法竞赛", "ACM", "ICPC"],
  robots: {
    index: true,
    follow: true,
  },
  openGraph: {
    type: "website",
    locale: "zh_CN",
    siteName: "PAA",
    title: "PAA 竞赛管理平台",
    description: "开源竞赛管理平台，提供组织介绍、竞赛报名、培训活动、证书与排名信息。",
  },
};

import { AntdRegistry } from '@ant-design/nextjs-registry';

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // Request-bound rendering lets Next attach the CSP nonce to hydration scripts.
  await headers();
  return (
    <html lang="zh-CN">
      <body>
        <GlobalFerrofluidBackground />
        <AntdRegistry>
          <GlobalRouteLoader />
          <ClientRuntime />
          <div className="app-content-layer">
            {children}
            <SiteFooter />
          </div>
        </AntdRegistry>
      </body>
    </html>
  );
}
