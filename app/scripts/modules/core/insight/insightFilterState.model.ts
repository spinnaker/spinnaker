import {module} from 'angular';

export class InsightFilterStateModel {

  public filtersPinned: boolean;
  public filtersExpanded: boolean;
  public filtersHovered: boolean;

  static get $inject() { return ['$rootScope', '$timeout', 'collapsibleSectionStateCache']; };

  public constructor(private $rootScope: ng.IRootScopeService,
                     private $timeout: ng.ITimeoutService,
                     private collapsibleSectionStateCache: any) {
    this.filtersExpanded = !collapsibleSectionStateCache.isSet('insightFilters') || collapsibleSectionStateCache.isExpanded('insightFilters');
    this.filtersPinned = this.filtersExpanded;
    this.filtersHovered = false;
  }

  public pinFilters(shouldPin: boolean): void {
    this.filtersPinned = shouldPin;
    this.filtersExpanded = shouldPin;
    this.collapsibleSectionStateCache.setExpanded('insightFilters', shouldPin);
    this.triggerReflow();
  }

  public hoverFilters(): void {
    if (!this.filtersHovered) {
      this.filtersHovered = true;
      this.filtersExpanded = true;
      this.triggerReflow();
    }
  }

  public exitFilters(): void {
    this.filtersHovered = false;
    if (!this.filtersPinned) {
      this.filtersExpanded = false;
      this.triggerReflow();
    }
  }

  public triggerReflow(): void {
    // wait 300ms to allow animation to complete
    this.$timeout(() => this.$rootScope.$broadcast('page-reflow'), 300);
  }
}

export const INSIGHT_FILTER_STATE_MODEL = 'spinnaker.core.insight.filter.state.model';
module(INSIGHT_FILTER_STATE_MODEL, [
  require('../cache/collapsibleSectionStateCache.js'),
]).service('InsightFilterStateModel', InsightFilterStateModel);
