'use strict';

angular.module('spinnaker.pipelines.trigger.cron.validation.service', [
  'restangular',
])
  .factory('cronValidationService', function(Restangular) {

    function validate(expression) {
      return Restangular.one('cron', 'validate').get({expression: expression}, {});
    }

    return {
      validate: validate,
    };
  });