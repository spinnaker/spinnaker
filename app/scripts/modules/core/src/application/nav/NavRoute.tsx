import React from 'react';
import { useIsActive, useSrefActive } from '@uirouter/react';

import { NavCategory } from './NavCategory';
import { ApplicationDataSource } from '../service/applicationDataSource';
import { Application } from '../../application';

export interface INavRouteProps {
  category: ApplicationDataSource;
  app: Application;
}

export const NavRoute = ({ app, category }: INavRouteProps) => {
  const sref = useSrefActive(category.sref, null, 'active');
  const isActive = useIsActive(category.activeState);

  return (
    <a {...sref}>
      <NavCategory app={app} category={category} isActive={isActive} />
    </a>
  );
};
