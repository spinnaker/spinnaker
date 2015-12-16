'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.cron.validation.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('cronValidationService', function(Restangular) {

    function validate(expression) {
      return Restangular.one('cron', 'validate').get({expression: expression}, {});
    }

    return {
      validate: validate,
    };
  });

