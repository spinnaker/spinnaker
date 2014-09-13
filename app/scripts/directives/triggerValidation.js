'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('triggerValidation', function () {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        var watches = attr.triggerValidation.split(',');
        watches.forEach(function(watchValue) {
          scope.$watch(watchValue, function () {
            if (ctrl.$viewValue) {
              ctrl.$setViewValue(ctrl.$viewValue);
            }
          });
        });
      }
    };
  }
);
