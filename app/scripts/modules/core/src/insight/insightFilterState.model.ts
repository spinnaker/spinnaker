import { module } from 'angular';

import { COLLAPSIBLE_SECTION_STATE_CACHE } from 'core/cache/collapsibleSectionStateCache';

export class InsightFilterStateModel {
  public filtersExpanded: boolean;

  public constructor(private collapsibleSectionStateCache: any) {
    'ngInject';
    this.filtersExpanded =
      !collapsibleSectionStateCache.isSet('insightFilters') ||
      collapsibleSectionStateCache.isExpanded('insightFilters');
  }

  public pinFilters(shouldPin: boolean): void {
    this.filtersExpanded = shouldPin;
    this.collapsibleSectionStateCache.setExpanded('insightFilters', shouldPin);
  }
}

export const INSIGHT_FILTER_STATE_MODEL = 'spinnaker.core.insight.insightFilterState.model';
module(INSIGHT_FILTER_STATE_MODEL, [COLLAPSIBLE_SECTION_STATE_CACHE]).service(
  'insightFilterStateModel',
  InsightFilterStateModel,
);
