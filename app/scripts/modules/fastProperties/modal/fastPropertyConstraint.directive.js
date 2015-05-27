'use strict';

angular
  .module('spinnaker.fastProperty.constraints.directive', [])
  .directive('fastPropertyConstraints', function() {
    return {
      restrict: 'E',
      //scope: {},
      templateUrl: 'scripts/modules/fastProperties/modal/fastPropertyConstraint.directive.html',
      //link: function (scope) {
      //
      //}
    };
  });
