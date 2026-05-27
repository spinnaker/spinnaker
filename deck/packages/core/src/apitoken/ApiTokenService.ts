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

import { REST } from '../api/ApiService';

export interface IApiToken {
  id: string;
  name: string;
  principalType: 'USER' | 'SERVICE_ACCOUNT';
  principalId: string;
  createdByUserId: string;
  /** Absent for non-expiring service account tokens. */
  expiresAt?: string | null;
  lastUsedAt?: string;
  createdAt?: number;
  lastModified?: number;
  /** Only present immediately after creation. Never stored or returned again. */
  token?: string;
}

export interface ICreateApiTokenRequest {
  name: string;
  principalType: 'USER' | 'SERVICE_ACCOUNT';
  principalId?: string;
  expiresAt?: string;
}

export interface IApiTokenServiceAccount {
  name: string;
  memberOf?: string[];
}

export const ApiTokenService = {
  listTokens(): PromiseLike<IApiToken[]> {
    return REST('/auth/apiTokens').get();
  },

  createToken(request: ICreateApiTokenRequest): PromiseLike<IApiToken & { token: string }> {
    return REST('/auth/apiTokens').post(request);
  },

  revokeToken(id: string): PromiseLike<void> {
    return REST('/auth/apiTokens').path(id).delete();
  },

  listApiTokenServiceAccounts(): PromiseLike<IApiTokenServiceAccount[]> {
    return REST('/auth/apiTokens/serviceAccounts').get();
  },

  /** Admin-only: returns all USER tokens across all principals. */
  listAllUserTokens(): PromiseLike<IApiToken[]> {
    return REST('/auth/apiTokens/admin/users').get();
  },
};
