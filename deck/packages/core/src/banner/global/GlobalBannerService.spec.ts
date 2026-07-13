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

import { mockHttpClient } from '../../api/mock/jasmine';
import type { IBannerRecord } from './GlobalBannerService';
import { GlobalBannerService } from './GlobalBannerService';

const BANNER: IBannerRecord = {
  id: 'maint-2026',
  message: '**Maintenance** window',
  color: 'var(--color-text-on-dark)',
  backgroundColor: 'var(--color-alert)',
  enabled: true,
  createdAt: 1000,
  updatedAt: 2000,
};

describe('GlobalBannerService', () => {
  describe('getActiveBanners()', () => {
    it('GETs /banners and returns the array', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/banners$/).respond(200, [BANNER]);

      let result: IBannerRecord[] | undefined;
      GlobalBannerService.getActiveBanners().then((r) => (result = r));

      await http.flush();
      expect(result).toEqual([BANNER]);
    });

    it('returns an empty array when there are no active banners', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/banners$/).respond(200, []);

      let result: IBannerRecord[] | undefined;
      GlobalBannerService.getActiveBanners().then((r) => (result = r));

      await http.flush();
      expect(result).toEqual([]);
    });
  });

  describe('getAllBanners()', () => {
    it('GETs /banners/all', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/banners\/all$/).respond(200, [BANNER]);

      let result: IBannerRecord[] | undefined;
      GlobalBannerService.getAllBanners().then((r) => (result = r));

      await http.flush();
      expect(result).toEqual([BANNER]);
    });
  });

  describe('getBannerById()', () => {
    it('GETs /banners/{id}', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/banners\/maint-2026$/).respond(200, BANNER);

      let result: IBannerRecord | undefined;
      GlobalBannerService.getBannerById('maint-2026').then((r) => (result = r));

      await http.flush();
      expect(result).toEqual(BANNER);
    });
  });

  describe('saveBanner()', () => {
    it('PUTs /banners with the record body', async () => {
      const http = mockHttpClient();
      http.expectPUT(/\/banners$/).respond(200, BANNER);

      let result: IBannerRecord | undefined;
      GlobalBannerService.saveBanner(BANNER).then((r) => (result = r));

      await http.flush();
      expect(result).toEqual(BANNER);
    });
  });

  describe('deleteBanner()', () => {
    it('DELETEs /banners/{id}', async () => {
      const http = mockHttpClient();
      http.expectDELETE(/\/banners\/maint-2026$/).respond(204, null);

      let resolved = false;
      GlobalBannerService.deleteBanner('maint-2026').then(() => (resolved = true));

      await http.flush();
      expect(resolved).toBe(true);
    });
  });

  describe('deleteAllBanners()', () => {
    it('DELETEs /banners', async () => {
      const http = mockHttpClient();
      http.expectDELETE(/\/banners$/).respond(204, null);

      let resolved = false;
      GlobalBannerService.deleteAllBanners().then(() => (resolved = true));

      await http.flush();
      expect(resolved).toBe(true);
    });
  });

  describe('forceRefresh()', () => {
    it('POSTs /banners/refresh', async () => {
      const http = mockHttpClient();
      http.expectPOST(/\/banners\/refresh$/).respond(200, null);

      let resolved = false;
      GlobalBannerService.forceRefresh().then(() => (resolved = true));

      await http.flush();
      expect(resolved).toBe(true);
    });
  });
});
