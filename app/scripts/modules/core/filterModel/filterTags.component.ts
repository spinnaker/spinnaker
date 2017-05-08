import { IComponentOptions, module } from 'angular';

export const filterTagsComponent: IComponentOptions = {
  bindings: {
    tags: '=',
    tagCleared: '&?',
    clearFilters: '&',
  },
  template: `
    <div class="col-md-12 filter-tags" ng-if="$ctrl.tags.length">
      Filtered by: <span class="filter-tag" ng-repeat="tag in $ctrl.tags">
          <strong>{{tag.label}}</strong>: {{tag.value}}
          <a href
             analytics-on="click" analytics-category="Filter Tags" analytics-event="Individual tag removed"
             ng-click="tag.clear(); $ctrl.tagCleared();"><span class="glyphicon glyphicon-remove-sign"></span></a>
        </span>
      <a href
         class="clear-filters"
         analytics-on="click" analytics-category="Filter Tags" analytics-event="Clear All clicked"
         ng-click="$ctrl.clearFilters()" ng-if="$ctrl.tags.length > 1">Clear All</a>
    </div>
`
};

export const FILTER_TAGS_COMPONENT = 'spinnaker.core.filterModel.filterTags.component';
module(FILTER_TAGS_COMPONENT, []).component('filterTags', filterTagsComponent);
