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

import React, { useEffect, useState } from 'react';

import { ApiTokensPage } from './ApiTokensPage';
import { REST } from '../api/ApiService';

interface IAuthUserResponse {
  isAdmin?: boolean;
  canMintApiTokens?: boolean;
  maxUserTokenLifetimeDays?: number;
  maxServiceAccountTokenLifetimeDays?: number;
}

export const ApiTokensPageContainer = () => {
  const [authUser, setAuthUser] = useState<IAuthUserResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    REST('/auth/user')
      .get<IAuthUserResponse>()
      .then((user: IAuthUserResponse) => {
        setAuthUser(user);
        setLoading(false);
      })
      .catch(() => {
        setError(true);
        setLoading(false);
      });
  }, []);

  if (loading) {
    return (
      <div className="container">
        <h3 className="text-center">
          <span className="glyphicon glyphicon-asterisk glyphicon-spinning" /> Loading…
        </h3>
      </div>
    );
  }

  if (error || !authUser) {
    return (
      <div className="container">
        <div className="alert alert-danger">Failed to load user information. Please refresh and try again.</div>
      </div>
    );
  }

  const isAdmin = authUser.isAdmin ?? false;
  return (
    <ApiTokensPage
      isAdmin={isAdmin}
      canMintApiTokens={authUser.canMintApiTokens ?? isAdmin}
      maxUserTokenLifetimeDays={authUser.maxUserTokenLifetimeDays ?? 0}
      maxServiceAccountTokenLifetimeDays={authUser.maxServiceAccountTokenLifetimeDays ?? 0}
    />
  );
};
