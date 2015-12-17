'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.cron.validator.directive', [
  require('./cron.validator.service.js'),
])
.directive('cronValidator', function($q, cronValidationService) {
    return {
      restrict: 'A',
      require: 'ngModel',
      link: function(scope, elem, attr, ctrl) {

        scope.cronErrors = scope.cronErrors || {};

        function handleError(result, deferred) {
          var message = result && result.message ? result.message : 'Error validating CRON expression';
          scope.cronErrors[attr.ngModel] = message;
          deferred.reject(message);
        }

        function handleSuccess(deferred) {
          deferred.resolve();
          delete scope.cronErrors[attr.ngModel];
        }

        ctrl.$asyncValidators.cronExpression = function(modelValue, viewValue) {
          var deferred = $q.defer();
          cronValidationService.validate(viewValue).then(
            function(result) {
              if (result.valid) {
                handleSuccess(deferred);
              } else {
                handleError(result, deferred);
              }
            },
            function(result) {
              handleError(result, deferred);
            }
          );
          return deferred.promise;
        };
      }
    };
  });
