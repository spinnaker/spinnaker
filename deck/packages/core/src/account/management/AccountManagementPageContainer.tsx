import React, { useEffect, useState } from 'react';

import { AccountManagementPage } from './AccountManagementPage';
import { REST } from '../../api/ApiService';

interface IAuthUserResponse {
  isAdmin?: boolean;
}

export const AccountManagementPageContainer = () => {
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
          You do not have permission to manage accounts. Contact a Spinnaker administrator.
        </div>
      </div>
    );
  }

  return <AccountManagementPage />;
};
