import { UIView, useCurrentStateAndParams, useRouter } from '@uirouter/react';
import React from 'react';
import { useRecoilValue } from 'recoil';

import type { Application } from '../application';
import { verticalNavExpandedAtom } from '../application/nav/navAtoms';
import { CollapsibleSectionStateCache } from '../cache';
import { FilterCollapse } from '../filterModel/FilterCollapse';

export interface IInsightLayoutProps {
  app: Application;
}

interface IInsightState {
  name?: string;
  views?: { [key: string]: unknown };
}

const INSIGHT_FILTERS_CACHE_KEY = 'insightFilters';

export const isClustersInsightState = (currentState: IInsightState): boolean => {
  return Boolean(currentState.name?.split('.').includes('clusters'));
};

export const shouldHideInsightFilters = (currentState: IInsightState, serverGroupsFetchOnDemand: boolean): boolean => {
  return isClustersInsightState(currentState) && serverGroupsFetchOnDemand;
};

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
  const [filtersExpanded, setFiltersExpanded] = React.useState(
    () =>
      !CollapsibleSectionStateCache.isSet(INSIGHT_FILTERS_CACHE_KEY) ||
      CollapsibleSectionStateCache.isExpanded(INSIGHT_FILTERS_CACHE_KEY),
  );
  const [serverGroupsFetchOnDemand, setServerGroupsFetchOnDemand] = React.useState(() =>
    Boolean(app.serverGroups?.fetchOnDemand),
  );
  const [currentLocation, setCurrentLocation] = React.useState(window.location.href);
  const filtersInitialized = React.useRef(false);
  const filterClass = filtersExpanded ? 'filters-expanded' : 'filters-collapsed';

  const toggleFilters = (): void => {
    setFiltersExpanded((expanded) => !expanded);
  };

  React.useEffect(() => {
    if (filtersInitialized.current) {
      CollapsibleSectionStateCache.setExpanded(INSIGHT_FILTERS_CACHE_KEY, filtersExpanded);
    } else {
      filtersInitialized.current = true;
    }
  }, [filtersExpanded]);

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

  React.useEffect(() => {
    const serverGroups = app.serverGroups;
    const updateFetchOnDemand = () => setServerGroupsFetchOnDemand(Boolean(serverGroups?.fetchOnDemand));
    updateFetchOnDemand();
    return serverGroups?.onRefresh(updateFetchOnDemand);
  }, [app]);

  const filtersHidden = shouldHideInsightFilters(currentState, serverGroupsFetchOnDemand);
  const showDetailsView = shouldShowDetailsView(currentState) || isInsightDetailUrl(currentLocation);
  const detailsClass = showDetailsView ? 'details-open' : 'details-closed';

  if (app.notFound || app.hasError) {
    return null;
  }

  return (
    <div className={`insight ${filterClass} ${navClass} ${detailsClass}`}>
      {!filtersHidden && (
        <div>
          <FilterCollapse filtersExpanded={filtersExpanded} onToggle={toggleFilters} />
        </div>
      )}
      {!filtersHidden && filtersExpanded && (
        <div className="nav">
          <UIView name="nav" />
        </div>
      )}
      <div className="flex-1">
        <div className="nav-content" data-scroll-id="nav-content">
          <UIView name="master" />
        </div>
        <div className="detail-content" style={{ display: showDetailsView ? undefined : 'none' }}>
          <UIView name="detail" />
        </div>
      </div>
    </div>
  );
};
