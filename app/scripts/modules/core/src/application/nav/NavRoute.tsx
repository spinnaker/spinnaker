import React from 'react';
import { useSrefActive } from '@uirouter/react';

import { NavCategory } from './NavCategory';
import { ApplicationDataSource } from '../service/applicationDataSource';
import { Application } from '../../application';

export interface INavRouteProps {
  category: ApplicationDataSource;
  isActive: boolean;
  app: Application;
}

export const NavRoute = ({ app, category, isActive }: INavRouteProps) => {
  const sref = useSrefActive(category.sref, null, 'active');
  return (
    <a {...sref}>
      <NavCategory app={app} category={category} isActive={isActive} />
    </a>
  );
};
