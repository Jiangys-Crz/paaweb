"use client";

import { useParams as useNextParams, usePathname, useRouter } from "next/navigation";
import { useEffect, useCallback } from "react";

export const useNavigate = () => {
  const router = useRouter();

  return useCallback((to: string | number, options?: { replace?: boolean }) => {
    if (typeof to === "number") {
      if (to < 0) {
        router.back();
      }
      return;
    }

    if (options?.replace) {
      router.replace(to);
    } else {
      router.push(to);
    }
  }, [router]);
};

export const useParams = <T extends Record<string, string | string[] | undefined> = Record<string, string>>() => {
  return useNextParams() as T;
};

export const useLocation = () => {
  const pathname = usePathname();
  return { pathname };
};

export const Navigate = ({ to, replace }: { to: string; replace?: boolean }) => {
  const router = useRouter();

  useEffect(() => {
    if (replace) {
      router.replace(to);
    } else {
      router.push(to);
    }
  }, [replace, router, to]);

  return null;
};
