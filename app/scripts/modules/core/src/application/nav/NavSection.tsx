import React from 'react';
import { NavRoute } from './NavRoute';

import { ApplicationDataSource } from '../service/applicationDataSource';
import { Application } from '../application.model';

export interface INavigationSectionProps {
  categories: ApplicationDataSource[];
  activeCategoryName: string;
  app: Application;
}

export const NavSection = ({ activeCategoryName, app, categories }: INavigationSectionProps) => (
  <div className="nav-section sp-padding-s-yaxis text-semibold">
    {categories.map(category => (
      <NavRoute key={category.label} category={category} app={app} isActive={category.label === activeCategoryName} />
    ))}
  </div>
);
