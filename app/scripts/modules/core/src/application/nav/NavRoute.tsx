import React from 'react';
import { useIsActive, useSref } from '@uirouter/react';

import { NavItem } from './NavItem';
import { ApplicationDataSource } from '../service/applicationDataSource';
import { Application } from '../../application';

export interface INavRouteProps {
  dataSource: ApplicationDataSource;
  app: Application;
}

export const NavRoute = ({ app, dataSource }: INavRouteProps) => {
  const sref = useSref(dataSource.sref);
  const isActive = useIsActive(dataSource.activeState);

  return (
    <a {...sref} className={isActive ? 'active' : ''}>
      <NavItem app={app} dataSource={dataSource} isActive={isActive} />
    </a>
  );
};
