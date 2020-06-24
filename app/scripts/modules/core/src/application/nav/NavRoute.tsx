import React from 'react';
import { useIsActive, useSrefActive } from '@uirouter/react';

import { NavItem } from './NavItem';
import { ApplicationDataSource } from '../service/applicationDataSource';
import { Application } from '../../application';

export interface INavRouteProps {
  dataSource: ApplicationDataSource;
  app: Application;
}

export const NavRoute = ({ app, dataSource }: INavRouteProps) => {
  const sref = useSrefActive(dataSource.sref, null, 'active');
  const isActive = useIsActive(dataSource.activeState);

  return (
    <a {...sref}>
      <NavItem app={app} dataSource={dataSource} isActive={isActive} />
    </a>
  );
};
