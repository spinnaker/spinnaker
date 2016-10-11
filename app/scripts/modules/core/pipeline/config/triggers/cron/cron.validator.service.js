'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.cron.validation.service', [
    require('core/api/api.service')
  ])
  .factory('cronValidationService', function(API) {

    function validate(expression) {
      return API.one('cron').one('validate').withParams({expression: expression}, {}).get();
    }

    return {
      validate: validate,
    };
  });

