'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.insight.filter.state.model', [
    require('../cache/collapsibleSectionStateCache.js'),
  ])
  .factory('InsightFilterStateModel', function($rootScope, $timeout, collapsibleSectionStateCache) {

    if (!collapsibleSectionStateCache.isSet('insightFilters')) {
      collapsibleSectionStateCache.setExpanded('insightFilters', true);
    }

    var vm = {
      filtersPinned: collapsibleSectionStateCache.isExpanded('insightFilters'),
      filtersExpanded: collapsibleSectionStateCache.isExpanded('insightFilters'),
      filtersHovered: false,
    };

    vm.pinFilters = (pin) => {
      vm.filtersPinned = pin;
      vm.filtersExpanded = pin;
      collapsibleSectionStateCache.setExpanded('insightFilters', pin);
      triggerReflow();
    };

    vm.hoverFilters = () => {
      if (!vm.filtersHovered) {
        vm.filtersHovered = true;
        vm.filtersExpanded = true;
        triggerReflow();
      }
    };

    vm.exitFilters = () => {
      vm.filtersHovered = false;
      if (!vm.filtersPinned) {
        vm.filtersExpanded = false;
        triggerReflow();
      }
    };

    function triggerReflow() {
      // wait 300ms to allow animation to complete
      $timeout(function() {
        $rootScope.$broadcast('page-reflow');
      }, 300);
    }

    return vm;

  }
);
