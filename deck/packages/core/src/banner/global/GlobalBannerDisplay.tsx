// Copyright 2026 Harness, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import React, { useEffect, useState } from 'react';

import type { IBannerRecord } from './GlobalBannerService';
import { GlobalBannerService } from './GlobalBannerService';
import { Markdown } from '../../presentation/Markdown';

/** How often (ms) to poll GET /banners. */
const POLL_INTERVAL_MS = 60_000;

/**
 * Renders a stack of active banners fetched from Gate.
 * Mounts above the main nav; silently hides itself when there are no active banners
 * or when the endpoint is unreachable.
 */
export function GlobalBannerDisplay() {
  const [banners, setBanners] = useState<IBannerRecord[]>([]);

  useEffect(() => {
    let cancelled = false;

    const fetch = () => {
      GlobalBannerService.getActiveBanners()
        .then((data) => {
          if (!cancelled) setBanners(data);
        })
        .catch(() => {
          // Silently swallow — a missing banner is not a fatal UI error.
        });
    };

    fetch();
    const id = setInterval(fetch, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  if (banners.length === 0) return null;

  return (
    <div className="global-banner-display">
      {banners.map((banner) => (
        <div
          key={banner.id}
          className="global-banner-display-item"
          style={{
            backgroundColor: banner.backgroundColor,
            color: banner.color,
            fontSize: banner.fontSize,
            textAlign: 'center',
          }}
        >
          <Markdown message={banner.message} />
        </div>
      ))}
    </div>
  );
}
