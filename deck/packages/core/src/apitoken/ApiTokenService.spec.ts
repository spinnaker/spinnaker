// Copyright 2026 DoorDash, Inc.
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

import { mockHttpClient } from '../api/mock/jasmine';
import type { IApiTokenServiceAccount, IApiToken, ICreateApiTokenRequest } from './ApiTokenService';
import { ApiTokenService } from './ApiTokenService';

const TOKEN_FIXTURE: IApiToken = {
  id: 'tok-uuid-1',
  name: 'ci-deploy',
  principalType: 'USER',
  principalId: 'alice@doordash.com',
  createdByUserId: 'alice@doordash.com',
  expiresAt: new Date(Date.now() + 30 * 86400 * 1000).toISOString(),
};

const SA_TOKEN_FIXTURE: IApiToken = {
  id: 'tok-uuid-2',
  name: 'ci-bot-token',
  principalType: 'SERVICE_ACCOUNT',
  principalId: 'ci-pipeline-bot',
  createdByUserId: 'admin@doordash.com',
  expiresAt: undefined,
};

const SA_FIXTURE: IApiTokenServiceAccount = {
  name: 'ci-pipeline-bot',
  memberOf: ['deploy-team'],
};

describe('ApiTokenService', () => {
  describe('listTokens()', () => {
    it('GETs /auth/apiTokens and returns the token array', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/auth\/apiTokens$/).respond(200, [TOKEN_FIXTURE]);

      let result: IApiToken[] | undefined;
      ApiTokenService.listTokens().then((tokens) => {
        result = tokens;
      });

      await http.flush();
      expect(result).toEqual([TOKEN_FIXTURE]);
    });

    it('returns an empty array when there are no tokens', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/auth\/apiTokens$/).respond(200, []);

      let result: IApiToken[] | undefined;
      ApiTokenService.listTokens().then((tokens) => {
        result = tokens;
      });

      await http.flush();
      expect(result).toEqual([]);
    });

    it('includes tokens with null expiresAt (non-expiring SA tokens)', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/auth\/apiTokens$/).respond(200, [SA_TOKEN_FIXTURE]);

      let result: IApiToken[] | undefined;
      ApiTokenService.listTokens().then((tokens) => {
        result = tokens;
      });

      await http.flush();
      expect(result![0].expiresAt).toBeUndefined();
    });
  });

  describe('createToken()', () => {
    it('POSTs to /auth/apiTokens with the request body', async () => {
      const http = mockHttpClient();
      const created = { ...TOKEN_FIXTURE, token: 'spk_abc123' };
      http.expectPOST(/\/auth\/apiTokens$/).respond(201, created);

      const request: ICreateApiTokenRequest = {
        name: 'ci-deploy',
        principalType: 'USER',
      };

      let result: (IApiToken & { token: string }) | undefined;
      ApiTokenService.createToken(request).then((token) => {
        result = token;
      });

      await http.flush();
      expect(result).toEqual(created);
      expect(result!.token).toBe('spk_abc123');
    });

    it('includes principalId and expiresAt when provided', async () => {
      const http = mockHttpClient();
      const expiry = new Date(Date.now() + 7 * 86400 * 1000).toISOString();
      const created = { ...SA_TOKEN_FIXTURE, token: 'spk_xyz789', expiresAt: expiry };
      http.expectPOST(/\/auth\/apiTokens$/).respond(201, created);

      const request: ICreateApiTokenRequest = {
        name: 'ci-bot-token',
        principalType: 'SERVICE_ACCOUNT',
        principalId: 'ci-pipeline-bot',
        expiresAt: expiry,
      };

      let result: (IApiToken & { token: string }) | undefined;
      ApiTokenService.createToken(request).then((token) => {
        result = token;
      });

      await http.flush();
      expect(result!.expiresAt).toBe(expiry);
    });

    it('handles creation of a non-expiring SA token (no expiresAt in request)', async () => {
      const http = mockHttpClient();
      const created = { ...SA_TOKEN_FIXTURE, token: 'spk_never' };
      http.expectPOST(/\/auth\/apiTokens$/).respond(201, created);

      const request: ICreateApiTokenRequest = {
        name: 'ci-bot-token',
        principalType: 'SERVICE_ACCOUNT',
        principalId: 'ci-pipeline-bot',
      };

      let result: (IApiToken & { token: string }) | undefined;
      ApiTokenService.createToken(request).then((token) => {
        result = token;
      });

      await http.flush();
      expect(result!.expiresAt).toBeUndefined();
    });
  });

  describe('revokeToken()', () => {
    it('DELETEs /auth/apiTokens/{id}', async () => {
      const http = mockHttpClient();
      http.expectDELETE(/\/auth\/apiTokens\/tok-uuid-1$/).respond(204, null);

      let resolved = false;
      ApiTokenService.revokeToken('tok-uuid-1').then(() => {
        resolved = true;
      });

      await http.flush();
      expect(resolved).toBe(true);
    });

    it('passes the correct token id in the path', async () => {
      const http = mockHttpClient();
      const customId = 'some-other-uuid-789';
      http.expectDELETE(new RegExp(`\\/auth\\/apiTokens\\/${customId}$`)).respond(204, null);

      ApiTokenService.revokeToken(customId);
      await http.flush();
    });
  });

  describe('listApiTokenServiceAccounts()', () => {
    it('GETs /auth/apiTokens/serviceAccounts', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/auth\/apiTokens\/serviceAccounts$/).respond(200, [SA_FIXTURE]);

      let result: IApiTokenServiceAccount[] | undefined;
      ApiTokenService.listApiTokenServiceAccounts().then((accounts) => {
        result = accounts;
      });

      await http.flush();
      expect(result).toEqual([SA_FIXTURE]);
    });

    it('returns an empty array when no API token service accounts are configured', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/auth\/apiTokens\/serviceAccounts$/).respond(200, []);

      let result: IApiTokenServiceAccount[] | undefined;
      ApiTokenService.listApiTokenServiceAccounts().then((accounts) => {
        result = accounts;
      });

      await http.flush();
      expect(result).toEqual([]);
    });
  });
});
