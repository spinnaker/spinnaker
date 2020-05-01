import React from 'react';
import { useCurrentStateAndParams } from '@uirouter/react';
import { find, isEqual } from 'lodash';

import { ApplicationRefresher } from './ApplicationRefresher';
import { ApplicationIcon } from '../ApplicationIcon';
import { NavSection } from './NavSection';
import { Icon, usePrevious } from '../../presentation';

import { navigationCategoryRegistry } from './navigationCategory.registry';
import { PagerDutyWriter } from 'core/pagerDuty';
import { Application } from '../application.model';
import { ApplicationDataSource } from '../service/applicationDataSource';

import './verticalNav.less';

export interface IApplicationNavigationProps {
  app: Application;
}

export const ApplicationNavigation = ({ app }: IApplicationNavigationProps) => {
  const prevDataSourceAttr = usePrevious(app.attributes.dataSources);
  useCurrentStateAndParams();

  const getNavigationCategories = (dataSources: ApplicationDataSource[]) => {
    const appSources = dataSources.filter(ds => ds.visible !== false && !ds.disabled && ds.sref);
    const allCategories = navigationCategoryRegistry.getAll();
    const categories = allCategories.map(c => appSources.filter(as => as.category === c.key));
    const uncategorizedSources = appSources.filter(
      as => !as.category || !find(allCategories, c => c.key == as.category),
    );
    categories.push(uncategorizedSources);
    return categories;
  };
  const initialCategories = getNavigationCategories(app.dataSources);
  const [navSections, setNavSections] = React.useState(initialCategories);

  const appRefreshSubscription = app.onRefresh(null, () => {
    if (!isEqual(app.attributes.dataSources, prevDataSourceAttr)) {
      const categories = getNavigationCategories(app.dataSources);
      setNavSections(categories);
    }
  });

  React.useEffect(() => {
    appRefreshSubscription();

    return () => {
      appRefreshSubscription();
    };
  }, []);

  const pageApplicationOwner = () => {
    PagerDutyWriter.pageApplicationOwnerModal(app);
  };

  return (
    <div className="vertical-navigation layer-high">
      <h3 className="heading-2 horizontal middle nav-header sp-margin-l sp-padding-l-bottom">
        <span className="hidden-xs sp-margin-l-right">
          <ApplicationIcon app={app} />
        </span>
        <span className="horizontal middle wrap">
          <span className="application-name text-semibold">{app.name}</span>
          <ApplicationRefresher app={app} />
        </span>
      </h3>
      {navSections
        .filter(section => section.length)
        .map((section, i) => (
          <NavSection key={`section-${i}`} categories={section} app={app} />
        ))}
      <div className="nav-section clickable">
        <div className="page-category flex-container-h middle" onClick={pageApplicationOwner}>
          <div className="nav-item sp-margin-s-right">
            <Icon className="nav-item-icon" name="spMenuPager" size="extraSmall" color="danger" />
          </div>
          <span> Page App Owner</span>
        </div>
      </div>
    </div>
  );
};
