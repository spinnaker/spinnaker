'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.clusterSelector.directive', [])
  .directive('scopeClusterSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        clusters: '=?',
        showIf: '=',
        onChange: '&',
        getClusters: '&?'

      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;
        vm.freeFormClusterField = false;

        vm.clusterChanged = () => {
          vm.onChange({cluster: vm.model});
        };

        vm.toggleFreeFormClusterField = function() {
          vm.freeFormClusterField = !vm.freeFormClusterField;
        };

        vm.getClusterList = () => {
          return vm.getClusters();
        };

      },
      templateUrl: require('./scopeClusterSelector.directive.html')
    };
  })
  .name;
