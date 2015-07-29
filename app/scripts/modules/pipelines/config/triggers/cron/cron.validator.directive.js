'use strict';

angular.module('spinnaker.pipelines.trigger.cron.validator.directive', [
  'spinnaker.pipelines.trigger.cron.validation.service',
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