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

import { GlobalBannerAdminPage } from './GlobalBannerAdminPage';
import { REST } from '../../api/ApiService';

interface IAuthUserResponse {
  isAdmin?: boolean;
}

export const GlobalBannerAdminPageContainer = () => {
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    REST('/auth/user')
      .get<IAuthUserResponse>()
      .then((user: IAuthUserResponse) => {
        setIsAdmin(user.isAdmin ?? false);
        setLoading(false);
      })
      .catch(() => {
        setIsAdmin(false);
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

  if (!isAdmin) {
    return (
      <div className="container">
        <div className="alert alert-warning">
          You do not have permission to manage global banners. Contact a Spinnaker administrator.
        </div>
      </div>
    );
  }

  return <GlobalBannerAdminPage />;
};
