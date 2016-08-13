'use strict';

let angular = require('angular');
require('./regionFilter.component.less');

module.exports = angular.module('spinnaker.deck.projects.dashboard.regionFilter.component', [
    require('./regionFilter.service.js'),
  ])
  .component('regionFilter', {
    bindings: {
      regionFilter: '=',
      regions: '='
    },
    templateUrl: require('./regionFilter.component.html'),
    controller: function (regionFilterService) {
      this.clearFilter = regionFilterService.clearFilter;
      this.toggleRegion = regionFilterService.toggleRegion;
      this.sortFilter = regionFilterService.sortFilter;
    }
  });
