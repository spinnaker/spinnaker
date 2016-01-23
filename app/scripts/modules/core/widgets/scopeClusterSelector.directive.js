'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.clusterSelector.directive', [])
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
        vm.freeFormClusterField = false;

        vm.toggleFreeFormClusterField = function(event) {
          event.preventDefault();
          vm.freeFormClusterField = !vm.freeFormClusterField;
        };

      },
      templateUrl: require('./scopeClusterSelector.directive.html')
    };
  });
