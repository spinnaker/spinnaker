'use strict';

import { module } from 'angular';

import { CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE } from './regionFilter.service';

import './regionFilter.component.less';

export const CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT =
  'spinnaker.deck.projects.dashboard.regionFilter.component';
export const name = CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT; // for backwards compatibility
module(CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT, [
  CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE,
]).component('regionFilter', {
  bindings: {
    regionFilter: '=',
    regions: '=',
  },
  templateUrl: require('./regionFilter.component.html'),
  controller: [
    'regionFilterService',
    function (regionFilterService) {
      this.clearFilter = regionFilterService.clearFilter;
      this.toggleRegion = regionFilterService.toggleRegion;
      this.sortFilter = regionFilterService.sortFilter;
    },
  ],
});
