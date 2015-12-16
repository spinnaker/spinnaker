'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.asgSelector.directive', [])
  .directive('scopeAsgSelector', function() {
    return {
      restrict: 'E',
      scope: {},
      bindToController: {
        model: '=',
        asgs: '=',
        showIf: '=',
        onChange: '&'
      },
      controllerAs: 'fp',
      controller: function controller() {
        var vm = this;
        vm.freeFormAsgField = false;

        vm.toggleFreeFormAsgField = function() {
          vm.freeFormAsgField = !vm.freeFormAsgField;
        };

        vm.asgChanged = () => {
          vm.onChange({asg: vm.model});
        };

      },
      templateUrl: require('./scopeAsgSelector.directive.html')
    };
  });
