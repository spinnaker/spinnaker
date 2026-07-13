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

import { REST } from '../../api/ApiService';

export interface IBannerRecord {
  id: string;
  message: string;
  /** CSS colour value for the text. */
  color?: string;
  /** CSS colour value for the background. */
  backgroundColor?: string;
  /** CSS font-size value (e.g. '14px'). */
  fontSize?: string;
  enabled: boolean;
  createdAt?: number;
  updatedAt?: number;
  /** Unix epoch ms. When set, the banner only activates after this time. */
  startTimestamp?: number;
  /** Unix epoch ms. When set, the banner deactivates after this time. */
  endTimestamp?: number;
}

export const GlobalBannerService = {
  /** Returns only active (enabled + within time window) banners. Public endpoint. */
  getActiveBanners(): PromiseLike<IBannerRecord[]> {
    return REST('/banners').get();
  },

  /** Returns all banners regardless of enabled/time state. Admin endpoint. */
  getAllBanners(): PromiseLike<IBannerRecord[]> {
    return REST('/banners/all').get();
  },

  getBannerById(id: string): PromiseLike<IBannerRecord> {
    return REST('/banners').path(id).get();
  },

  saveBanner(record: IBannerRecord): PromiseLike<IBannerRecord> {
    return REST('/banners').put(record);
  },

  deleteBanner(id: string): PromiseLike<void> {
    return REST('/banners').path(id).delete();
  },

  deleteAllBanners(): PromiseLike<void> {
    return REST('/banners').delete();
  },

  forceRefresh(): PromiseLike<void> {
    return REST('/banners/refresh').post();
  },
};
