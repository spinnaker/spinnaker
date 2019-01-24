'use strict';

const angular = require('angular');

import './regionFilter.component.less';

module.exports = angular
  .module('spinnaker.deck.projects.dashboard.regionFilter.component', [require('./regionFilter.service').name])
  .component('regionFilter', {
    bindings: {
      regionFilter: '=',
      regions: '=',
    },
    templateUrl: require('./regionFilter.component.html'),
    controller: function(regionFilterService) {
      this.clearFilter = regionFilterService.clearFilter;
      this.toggleRegion = regionFilterService.toggleRegion;
      this.sortFilter = regionFilterService.sortFilter;
    },
  });
