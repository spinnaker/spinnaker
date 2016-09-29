'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastproperty.regionSelector.component', [])
  .component('regionSelector', {
    bindings: {
      model: '=',
      regions: '=',
      showIf: '=',
      onChange: '&',
    },
    templateUrl: require('./regionSelector.component.html'),
    controllerAs: 'fp',
    controller: function controller($scope) {
      var vm = this;

      vm.allowNone = _.isBoolean($scope.allowNone) ? $scope.allowNone : true;

      vm.regionChanged = () => {
        vm.onChange({region: vm.model});
      };

    },
  });
