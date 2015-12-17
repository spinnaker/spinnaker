'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.stackSelector.directive', [])
  .directive('scopeStackSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        stacks: '=',
        showIf: '=',
        onChange: '&'
      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;

        vm.stackChanged = () => {
          vm.onChange({stack: vm.model});
        };

      },
      templateUrl: require('./scopeStackSelector.directive.html')
    };
  });
