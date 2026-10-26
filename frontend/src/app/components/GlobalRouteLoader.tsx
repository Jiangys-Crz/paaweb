"use client";

import { useEffect, useState } from 'react';
import PageLoader from './PageLoader';

// The logo is a first-visit welcome only. Route changes and background requests
// must never block the interface with this full-screen layer.
const INITIAL_VISIBLE_TIME = 220;

const GlobalRouteLoader = () => {
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (window.__paa_first_load_done__) return;

    setLoading(true);

    const timer = window.setTimeout(() => {
      window.__paa_first_load_done__ = true;
      setLoading(false);
    }, INITIAL_VISIBLE_TIME);

    return () => window.clearTimeout(timer);
  }, []);

  return <PageLoader loading={loading} initialOnly={false} />;
};

export default GlobalRouteLoader;
