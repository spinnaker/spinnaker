'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.trigger.cron.validator.directive', [require('./cron.validator.service').name])
  .directive('cronValidator', [
    '$q',
    'cronValidationService',
    function($q, cronValidationService) {
      return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope, elem, attr, ctrl) {
          let validationMessages = scope.$eval(attr.cronValidationMessages) || {};

          function handleError(result, deferred) {
            var message = result && result.message ? result.message : 'Error validating CRON expression';
            validationMessages.error = message;
            delete validationMessages.description;
            deferred.reject(message);
          }

          function handleSuccess(result, deferred) {
            deferred.resolve();
            if (result.description && result.description.length) {
              validationMessages.description = result.description.charAt(0).toLowerCase() + result.description.slice(1);
            } else {
              validationMessages.description = '';
            }
            delete validationMessages.error;
          }

          ctrl.$asyncValidators.cronExpression = function(modelValue, viewValue) {
            // compress whitespace - the actual scheduler does not care about it, but the expression validator does
            // for some reason
            if (viewValue) {
              viewValue = viewValue.replace(/\s\s+/g, ' ');
            }
            var deferred = $q.defer();
            cronValidationService.validate(viewValue).then(
              function(result) {
                if (result.valid) {
                  handleSuccess(result, deferred);
                } else {
                  handleError(result, deferred);
                }
              },
              function(result) {
                handleError(result, deferred);
              },
            );
            return deferred.promise;
          };
        },
      };
    },
  ]);
