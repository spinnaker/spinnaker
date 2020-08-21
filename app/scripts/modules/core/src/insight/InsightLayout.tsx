import React from 'react';
import { UIView, useCurrentStateAndParams } from '@uirouter/react';

import { ReactInjector } from 'core/reactShims';
import { FilterCollapse } from 'core/filterModel/FilterCollapse';
import { Application } from 'core/application';

export interface IInsightLayoutProps {
  app: Application;
}

export const InsightLayout = ({ app }: IInsightLayoutProps) => {
  const [expandFilters, setExpandFilters] = React.useState(ReactInjector.insightFilterStateModel.filtersExpanded);
  const { filtersHidden } = ReactInjector.insightFilterStateModel;
  const filterClass = expandFilters ? 'filters-expanded' : 'filters-collapsed';

  const toggleFilters = (): void => {
    ReactInjector.insightFilterStateModel.pinFilters(!expandFilters);
    setExpandFilters(!expandFilters);
  };

  const { state: currentState } = useCurrentStateAndParams();
  const showDetailsView = Boolean(Object.keys(currentState.views).find(v => v.indexOf('detail@') !== -1));

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
      {!filtersHidden && expandFilters && (
        <div>
          <UIView name="nav" className="nav ng-scope" />
        </div>
      )}
      <div className="flex-1">
        <UIView name="master" className="nav-content ng-scope" data-scroll-id="nav-content" />
        {showDetailsView && (
          <div>
            <UIView name="detail" className="detail-content" />
          </div>
        )}
      </div>
    </div>
  );
};
