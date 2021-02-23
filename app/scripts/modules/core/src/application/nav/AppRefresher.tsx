import React from 'react';

import { AppRefresherIcon } from './AppRefresherIcon';
import { Application } from '../application.model';
import { useObservable } from '../../presentation/hooks';
import { IFetchStatus } from '../service/applicationDataSource';

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
