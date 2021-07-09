import { useCurrentStateAndParams } from '@uirouter/react';
import classnames from 'classnames';
import { find, isEqual } from 'lodash';
import React from 'react';
import { useRecoilValue } from 'recoil';

import { Icon } from '@spinnaker/presentation';

import { AppRefresher } from './AppRefresher';
import { BottomSection } from './BottomSection';
import { NavSection } from './NavSection';
import { Application } from '../application.model';
import { SETTINGS } from '../../config/settings';
import { verticalNavExpandedAtom } from './navAtoms';
import { navigationCategoryRegistry } from './navigationCategory.registry';
import { PagerDutyWriter } from '../../pagerDuty';
import { Tooltip, useIsMobile, usePrevious } from '../../presentation';
import { ApplicationDataSource } from '../service/applicationDataSource';

import './verticalNav.less';

export interface IApplicationNavigationProps {
  app: Application;
}

export const ApplicationNavigation = ({ app }: IApplicationNavigationProps) => {
  const prevDataSourceAttr = usePrevious(app.attributes.dataSources);
  useCurrentStateAndParams();
  const isMobile = useIsMobile();

  const isExpanded = useRecoilValue(verticalNavExpandedAtom);

  const getNavigationCategories = (dataSources: ApplicationDataSource[]) => {
    const appSources = dataSources.filter((ds) => ds.visible !== false && !ds.disabled && ds.sref);
    const allCategories = navigationCategoryRegistry.getAll();
    const categories = allCategories.map((c) => appSources.filter((as) => as.category === c.key));
    const uncategorizedSources = appSources.filter(
      (as) => !as.category || !find(allCategories, (c) => c.key == as.category),
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
    const unsubscribe = appRefreshSubscription();
    return unsubscribe;
  }, []);

  const pageApplicationOwner = () => {
    PagerDutyWriter.pageApplicationOwnerModal(app);
  };

  if (!isExpanded && isMobile) {
    return null;
  }

  return (
    <div className={classnames(['vertical-navigation', 'layer-high', { 'vertical-nav-collapsed': !isExpanded }])}>
      <h3 className="heading-2 horizontal middle nav-header sp-margin-m-xaxis sp-margin-l-top">
        <AppRefresher app={app} />
        <span className="application-name text-semibold heading-2 sp-margin-m-left">{app.name}</span>
      </h3>
      <div className="nav-content">
        {navSections
          .filter((section) => section.length)
          .map((section, i) => (
            <NavSection key={`section-${i}`} dataSources={section} app={app} />
          ))}
        {SETTINGS.feature.pagerDuty && app.attributes.pdApiKey && (
          <div className="nav-section sp-padding-s-yaxis">
            <div
              className="page-category flex-container-h middle text-semibold sp-padding-s-yaxis clickable"
              onClick={pageApplicationOwner}
            >
              <div className="nav-row-item sp-margin-s-right">
                {!isExpanded ? (
                  <Tooltip value="Page App Owner" placement="right">
                    <div>
                      <Icon className="nav-item-icon" name="spMenuPager" size="medium" color="danger" />
                    </div>
                  </Tooltip>
                ) : (
                  <Icon className="nav-item-icon" name="spMenuPager" size="medium" color="danger" />
                )}
              </div>
              <span className="nav-name"> Page App Owner</span>
            </div>
          </div>
        )}
      </div>
      <div className="nav-bottom">
        <BottomSection app={app} />
      </div>
    </div>
  );
};
