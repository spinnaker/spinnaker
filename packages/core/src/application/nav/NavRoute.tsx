import { useIsActive, useSref } from '@uirouter/react';
import React from 'react';

import { NavItem } from './NavItem';
import { Application } from '../../application';
import { ApplicationDataSource } from '../service/applicationDataSource';

export interface INavRouteProps {
  dataSource: ApplicationDataSource;
  app: Application;
}

export const NavRoute = ({ app, dataSource }: INavRouteProps) => {
  const sref = useSref(dataSource.sref);
  const isActive = useIsActive(dataSource.activeState);

  return (
    <a {...sref} className={`nav-category flex-container-h middle sp-padding-s-yaxis${isActive ? ' active' : ''}`}>
      <NavItem app={app} dataSource={dataSource} isActive={isActive} />
    </a>
  );
};
