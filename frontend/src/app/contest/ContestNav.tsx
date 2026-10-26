"use client";

import SiteHeader from '../components/SiteHeader';

interface ActivityLayoutProps {
  children: React.ReactNode;
}

const ActivityLayout = ({ children }: ActivityLayoutProps) => {
  return (
    <>
      <SiteHeader />
      <main className="content-page-shell activity-shell">
        {children}
      </main>
    </>
  );
};

export default ActivityLayout;
