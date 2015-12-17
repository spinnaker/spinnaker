'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.availabilityZoneSelector.directive', [])
  .directive('scopeAvailabilityZoneSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        zones: '=',
        showIf: '=',
        onChange: '&'
      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;

        vm.zoneChanged = () => {
          vm.onChange({zone: vm.model});
        };

      },
      templateUrl: require('./scopeAvailabilityZoneSelector.directive.html')
    };
  });
