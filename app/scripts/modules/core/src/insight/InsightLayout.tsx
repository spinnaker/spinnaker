import React from 'react';
import { UIView } from '@uirouter/react';

import { ReactInjector } from 'core/reactShims';
import { FilterCollapse } from 'core/filterModel/FilterCollapse';
import { Application } from 'core/application';

export interface IInsightLayoutProps {
  app: Application;
}

export const InsightLayout = ({ app }: IInsightLayoutProps) => {
  const [appIsReady, setAppIsReady] = React.useState(false);
  const [expandFilters, setExpandFilters] = React.useState(ReactInjector.insightFilterStateModel.filtersExpanded);
  const { filtersHidden } = ReactInjector.insightFilterStateModel;
  const filterClass = expandFilters ? 'filters-expanded' : 'filters-collapsed';

  const toggleFilters = (): void => {
    ReactInjector.insightFilterStateModel.pinFilters(!expandFilters);
    setExpandFilters(!expandFilters);
  };

  React.useEffect(() => {
    app.ready().then(() => setAppIsReady(true));
  }, []);

  if (app.notFound || app.hasError) {
    return null;
  }

  return (
    <div className={`insight ${filterClass}`}>
      {!filtersHidden && (
        <div onClick={toggleFilters}>
          <FilterCollapse />
        </div>
      )}
      {!filtersHidden && (
        <div>
          <UIView name="nav" className="nav ng-scope" />
        </div>
      )}
      <div>
        <UIView name="master" className="nav-content ng-scope" data-scroll-id="nav-content" />
      </div>
      {appIsReady && (
        <div>
          <UIView name="detail" className="detail-content" />
        </div>
      )}
    </div>
  );
};
