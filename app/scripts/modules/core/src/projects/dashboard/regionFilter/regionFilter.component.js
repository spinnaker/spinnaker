'use strict';

const angular = require('angular');

import './regionFilter.component.less';

export const CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT =
  'spinnaker.deck.projects.dashboard.regionFilter.component';
export const name = CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT; // for backwards compatibility
angular
  .module(CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT, [require('./regionFilter.service').name])
  .component('regionFilter', {
    bindings: {
      regionFilter: '=',
      regions: '=',
    },
    templateUrl: require('./regionFilter.component.html'),
    controller: [
      'regionFilterService',
      function(regionFilterService) {
        this.clearFilter = regionFilterService.clearFilter;
        this.toggleRegion = regionFilterService.toggleRegion;
        this.sortFilter = regionFilterService.sortFilter;
      },
    ],
  });
