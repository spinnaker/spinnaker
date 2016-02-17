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
        toggled: '&?',
      },
      controllerAs: 'vm',
      controller: function controller() {
        var vm = this;

        vm.toggled = vm.toggled || angular.noop;

        let selectedNotInClusterList = () => {
          return !(angular.isArray(vm.clusters) && vm.clusters.length && vm.clusters.some((cluster) => cluster === vm.model));
        };

        let modelIsSet = () => {
          return vm.model !== undefined || vm.model !== null || vm.model.trim() !== '';
        };

        vm.freeFormClusterField = modelIsSet() ? selectedNotInClusterList() : false;

        vm.toggleFreeFormClusterField = function(event) {
          event.preventDefault();
          vm.freeFormClusterField = !vm.freeFormClusterField;
          vm.toggled({isToggled: vm.freeFormClusterField});
        };

      },
      templateUrl: require('./scopeClusterSelector.directive.html')
    };
  });
