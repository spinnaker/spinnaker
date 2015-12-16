'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.instanceSelector.directive', [])
  .directive('scopeInstanceSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        instances: '=',
        showIf: '=',
        onChange: '&'
      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;

        vm.instanceChanged = () => {
          vm.onChange({instance: vm.model});
        };

      },
      templateUrl: require('./scopeInstanceSelector.directive.html')
    };
  });
