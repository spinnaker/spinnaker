import { UIView, useCurrentStateAndParams } from '@uirouter/react';
import React from 'react';
import { useRecoilValue } from 'recoil';

import { Application } from '../application';
import { verticalNavExpandedAtom } from '../application/nav/navAtoms';
import { FilterCollapse } from '../filterModel/FilterCollapse';
import { ReactInjector } from '../reactShims';

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

  const navClass = useRecoilValue(verticalNavExpandedAtom) ? 'nav-expanded' : 'nav-collapsed';

  const { state: currentState } = useCurrentStateAndParams();
  const showDetailsView = Boolean(Object.keys(currentState.views).find((v) => v.indexOf('detail@') !== -1));
  const detailsClass = showDetailsView ? 'details-open' : 'details-closed';

  if (app.notFound || app.hasError) {
    return null;
  }

  return (
    <div className={`insight ${filterClass} ${navClass} ${detailsClass}`}>
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
