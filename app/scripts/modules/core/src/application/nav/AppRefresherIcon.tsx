import { $window } from 'ngimport';
import React from 'react';

import { Illustration } from '@spinnaker/presentation';

import { ApplicationFreshIcon } from '../ApplicationFreshIcon';
import { Tooltip } from '../../presentation';
import { SchedulerFactory } from '../../scheduler';
import { relativeTime, timestamp } from '../../utils/timeFormatters';

export interface IAppRefreshIconProps {
  appName: string;
  lastRefresh: number;
  refresh: () => void;
  refreshing: boolean;
}

export const AppRefresherIcon = ({ appName, lastRefresh, refresh, refreshing }: IAppRefreshIconProps) => {
  const activeRefresher = SchedulerFactory.createScheduler(2000);
  const [timeSinceRefresh, setTimeSinceRefresh] = React.useState(relativeTime(lastRefresh));
  const [iconPulsing, setIconPulsing] = React.useState(false);

  React.useEffect(() => {
    activeRefresher.subscribe(() => {
      setTimeSinceRefresh(relativeTime(lastRefresh));
    });

    return () => activeRefresher.unsubscribe();
  }, [lastRefresh]);

  const oldAge = 2 * 60 * 1000; // 2 minutes;
  const age = new Date().getTime() - lastRefresh;
  const isStale = age > oldAge;

  const refreshApp = () => {
    setIconPulsing(true);
    setTimeout(() => {
      setIconPulsing(false);
    }, 3000);
    refresh();
  };

  const RefresherTooltip = (
    <span>
      <p>
        <strong>{appName}</strong>
      </p>
      <p>{`app data is ${refreshing ? 'refreshing' : 'up to date (click to refresh)'}`}</p>
      <p>
        Last refresh: {timestamp(lastRefresh)} <br /> {timeSinceRefresh}
      </p>
      <p>Note: Due to caching, data may be delayed up to 2 minutes</p>
    </span>
  );
  const isRefreshing = iconPulsing || refreshing;

  return (
    <Tooltip template={RefresherTooltip} placement={$window.innerWidth < 1100 ? 'bottom' : 'right'}>
      <div className={`application-header-icon${isRefreshing ? ' header-icon-pulsing' : ''}`} onClick={refreshApp}>
        {!isStale && !isRefreshing && <ApplicationFreshIcon />}
        {(isStale || isRefreshing) && <Illustration className="app-fresh-icon horizontal middle" name="appUnsynced" />}
      </div>
    </Tooltip>
  );
};
