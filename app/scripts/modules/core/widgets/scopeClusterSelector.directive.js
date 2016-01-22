'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.clusterSelector.directive', [])
  .directive('clusterSelector', function() {
    return {
      restrict: 'E',
      bindToController: {
        model: '=?',
        clusters: '=?',
        onChange: '&?',
      },
      controllerAs: 'vm',
      controller: function controller() {
        var vm = this;
        vm.freeFormClusterField = false;

        vm.clusterChanged = () => {
          vm.onChange ? vm.onChange({cluster: vm.model}) : angular.noop();
        };

        vm.toggleFreeFormClusterField = function(event) {
          event.preventDefault();
          vm.freeFormClusterField = !vm.freeFormClusterField;
        };

        vm.getClusterList = () => {
          return vm.getClusters();
        };

      },
      templateUrl: require('./scopeClusterSelector.directive.html')
    };
  });
