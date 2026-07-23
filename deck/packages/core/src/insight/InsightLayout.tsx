import { UIView, useCurrentStateAndParams, useRouter } from '@uirouter/react';
import React from 'react';
import { useRecoilValue } from 'recoil';
import { AngularServices } from '../angular/services';

import type { Application } from '../application';
import { verticalNavExpandedAtom } from '../application/nav/navAtoms';
import { FilterCollapse } from '../filterModel/FilterCollapse';

export interface IInsightLayoutProps {
  app: Application;
}

interface IInsightState {
  name?: string;
  views?: { [key: string]: unknown };
}

export const shouldShowDetailsView = (currentState: IInsightState): boolean => {
  if (Object.keys(currentState.views || {}).some((v) => v.indexOf('detail@') !== -1)) {
    return true;
  }

  const insightStateName = currentState.name?.split('.insight.')[1];
  return Boolean(insightStateName && insightStateName.split('.').length > 1);
};

export const isInsightDetailUrl = (href: string): boolean => {
  return /\/(?:instanceDetails|serverGroupDetails|loadBalancerDetails|targetGroupDetails|firewallDetails|functionDetails|multipleInstances|multipleServerGroups)(?:\/|$)/.test(
    href,
  );
};

export const InsightLayout = ({ app }: IInsightLayoutProps) => {
  const router = useRouter();
  const [expandFilters, setExpandFilters] = React.useState(AngularServices.insightFilterStateModel.filtersExpanded);
  const [currentLocation, setCurrentLocation] = React.useState(window.location.href);
  const filterClass = expandFilters ? 'filters-expanded' : 'filters-collapsed';

  const toggleFilters = (): void => {
    AngularServices.insightFilterStateModel.pinFilters(!expandFilters);
    setExpandFilters(!expandFilters);
  };

  const navClass = useRecoilValue(verticalNavExpandedAtom) ? 'nav-expanded' : 'nav-collapsed';

  const { state: hookState } = useCurrentStateAndParams();
  const [currentState, setCurrentState] = React.useState<IInsightState>(
    () => hookState || router.globals.current || {},
  );

  React.useEffect(() => {
    setCurrentState(hookState || router.globals.current || {});
  }, [hookState]);

  React.useEffect(() => {
    const removeTransitionHook = router.transitionService.onSuccess({}, (transition: any) => {
      setCurrentState(transition.to() || {});
      setCurrentLocation(window.location.href);
    });

    return () => removeTransitionHook();
  }, [router]);

  React.useEffect(() => {
    const handleLocationChange = () => setCurrentLocation(window.location.href);
    window.addEventListener('hashchange', handleLocationChange);

    return () => window.removeEventListener('hashchange', handleLocationChange);
  }, []);

  const { filtersHidden } = AngularServices.insightFilterStateModel;
  const showDetailsView = shouldShowDetailsView(currentState) || isInsightDetailUrl(currentLocation);
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
        <div className="nav ng-scope">
          <UIView name="nav" />
        </div>
      )}
      <div className="flex-1">
        <div className="nav-content ng-scope" data-scroll-id="nav-content">
          <UIView name="master" />
        </div>
        <div className="detail-content" style={{ display: showDetailsView ? undefined : 'none' }}>
          <UIView name="detail" />
        </div>
      </div>
    </div>
  );
};
