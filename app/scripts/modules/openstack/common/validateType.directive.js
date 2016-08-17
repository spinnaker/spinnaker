'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.validateType.directive', [
])
.directive('validateType', [
  function() {

    var link = function($scope, $element, $attrs, ctrl) {

      var validate = function(viewValue) {
        var comparisonModel = $attrs.validateType;

        if(parseInt(viewValue, 10) === -1 && parseInt(comparisonModel, 10) > -1)  {
          ctrl.$setValidity('validateType', false);
        }
        else {
          ctrl.$setValidity('validateType', true);
        }
        return viewValue;
      };

      ctrl.$parsers.unshift(validate);
      ctrl.$formatters.push(validate);

      $attrs.$observe('validateType', function() {
        return validate(ctrl.$viewValue);
      });
    };

    return {
      require: 'ngModel',
      link: link
    };

  }
]);
