'use strict';

let angular = require('angular');

/**
 * This directive is responsible for setting the validity of the name field when creating a new application.
 * It does NOT render the error/warning messages to the screen - that is handled by the
 * applicationNameValidationMessages directive.
 */
module.exports = angular
  .module('spinnaker.core.application.modal.validateApplicationName.directive', [
    require('./applicationName.validator.js'),
  ])
  .directive('validateApplicationName', function (applicationNameValidator) {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function (scope, elem, attr, ctrl) {
        scope.$watch(attr.cloudProviders, function (newVal, oldVal) {
          if (newVal !== oldVal && (ctrl.$viewValue || ctrl.$dirty)) {
            ctrl.$validate();
          }
        }, true);

        ctrl.$validators.validateApplicationName = (value) => {
          var cloudProviders = scope.$eval(attr.cloudProviders) || [];
          return applicationNameValidator.validate(value, cloudProviders).errors.length === 0;
        };
      }
    };
  }
);
