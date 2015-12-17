'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.scope.regionSelector.directive', [
    require('../../../../../core/utils/lodash')
  ])
  .directive('scopeRegionSelector', function() {
    return {
      restrict: 'E',
      scope: {
        allowNone: '=?',
      },
      bindToController: {
        model: '=',
        regions: '=',
        onChange: '&',
      },
      controllerAs: 'fp',
      controller: function controller($scope, _) {
        var vm = this;

        vm.allowNone = _.isBoolean($scope.allowNone) ? $scope.allowNone : true;

        vm.regionChanged = () => {
          vm.onChange({region: vm.model});
        };

      },
      templateUrl: require('./scopeRegionSelector.directive.html')
    };
  });
