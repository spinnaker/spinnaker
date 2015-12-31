'use strict';

const angular = require('angular');

/**
 * This directive is responsible for rendering error and warning messages to the screen when creating a new application.
 * It does NOT set the validity of the form field - that is handled by the validateApplicationName directive.
 */
module.exports = angular
  .module('spinnaker.core.application.config.applicationNameValidationMessages.directive', [
    require('./applicationName.validator.js'),
  ])
  .directive('applicationNameValidationMessages', function () {
    return {
      restrict: 'E',
      templateUrl: require('./applicationNameValidationMessages.directive.html'),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ApplicationNameValidationMessagesCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ApplicationNameValidationMessagesCtrl', function ($scope, applicationNameValidator) {

    let validate = () => {
      let validation = applicationNameValidator.validate(this.application.name, this.application.cloudProviders);
      this.warnings = validation.warnings;
      this.errors = validation.errors;
    };

    $scope.$watch(() => this.application.name, validate);
    $scope.$watch(() => this.application.cloudProviders, validate);

  });
