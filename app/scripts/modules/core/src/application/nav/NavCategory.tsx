import React from 'react';

import { DataSourceNotifications } from 'core/entityTag/notifications/DataSourceNotifications';
import { Icon, useDataSource } from '../../presentation';

import { ApplicationDataSource } from '../service/applicationDataSource';
import { Application } from '../application.model';
import { IEntityTags } from '../../domain';

export interface INavCategoryProps {
  category: ApplicationDataSource;
  isActive: boolean;
  app: Application;
}

export const NavCategory = ({ app, category, isActive }: INavCategoryProps) => {
  const { alerts, badge, iconName, key, label } = category;

  const { data: badgeData } = useDataSource(app.getDataSource(badge || key));
  const runningCount = badge ? badgeData.length : 0;

  // useDataSource is enough to update alerts when needed
  useDataSource(category);
  const tags: IEntityTags[] = alerts || [];

  const badgeClassNames = runningCount ? 'badge-running-count' : 'badge-none';

  return (
    <div className="nav-category flex-container-h middle sp-padding-s-yaxis'">
      <div className={badgeClassNames}>{runningCount > 0 ? runningCount : ''}</div>
      <div className="nav-item">
        {iconName && (
          <Icon className="nav-icon" name={iconName} size="extraSmall" color={isActive ? 'primary' : 'accent'} />
        )}
      </div>
      <div className="nav-item">{' ' + category.label}</div>
      <DataSourceNotifications tags={tags} application={app} tabName={label} />
    </div>
  );
};
