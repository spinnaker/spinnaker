'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.regionSelector.directive', [])
  .directive('scopeRegionSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        regions: '=',
        onChange: '&'
      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;

        vm.regionChanged = () => {
          vm.onChange({region: vm.model});
        };

      },
      templateUrl: require('./scopeRegionSelector.directive.html')
    };
  })
  .name;
