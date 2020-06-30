import React from 'react';

import { useObservable } from '../../presentation/hooks';
import { Application } from '../application.model';
import { IFetchStatus } from '../service/applicationDataSource';

import { AppRefresherIcon } from './AppRefresherIcon';

export interface IAppRefresherProps {
  app: Application;
}

export const AppRefresher = ({ app }: IAppRefresherProps) => {
  const [isRefreshing, setIsRefreshing] = React.useState(false);
  const [lastRefresh, setLastRefresh] = React.useState(0);

  const updateStatus = (fetchStatus: IFetchStatus): void => {
    if (fetchStatus.status === 'FETCHING') {
      setIsRefreshing(true);
    } else if (fetchStatus.status === 'FETCHED') {
      setIsRefreshing(false);
      setLastRefresh(fetchStatus.lastRefresh);
    } else {
      setIsRefreshing(false);
    }
  };

  useObservable(app.status$, updateStatus);

  const handleRefresh = (): void => {
    app.refresh(true);
  };

  return (
    <AppRefresherIcon appName={app.name} lastRefresh={lastRefresh} refreshing={isRefreshing} refresh={handleRefresh} />
  );
};
