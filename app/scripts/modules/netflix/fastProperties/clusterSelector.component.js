'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastproperty.clusterSelector.component', [])
  .component('clusterSelectorComponent', {
    bindings: {
      model: '=',
      clusters: '=?',
      onChange: '&',
      getClusters: '&?'
    },
    templateUrl: require('./clusterSelector.component.html'),
    controllerAs: 'fp',
    controller: function controller() {
      var vm = this;
      vm.freeFormClusterField = true;

      vm.allowNone = true;// _.isBoolean($scope.allowNone) ? $scope.allowNone : true;

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
  });
