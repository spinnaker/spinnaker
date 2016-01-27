'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.widget.clusterSelector.directive', [])
  .directive('clusterSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        clusters: '=',
      },
      controllerAs: 'vm',
      controller: function controller() {
        var vm = this;
        vm.freeFormClusterField = vm.model === undefined ? false : !vm.clusters.some(cluster => cluster === vm.model);

        vm.toggleFreeFormClusterField = function(event) {
          event.preventDefault();
          vm.freeFormClusterField = !vm.freeFormClusterField;
        };

      },
      templateUrl: require('./scopeClusterSelector.directive.html')
    };
  });
