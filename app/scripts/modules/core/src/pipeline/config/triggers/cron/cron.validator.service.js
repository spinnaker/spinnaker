'use strict';

import { API } from 'core/api/ApiService';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.trigger.cron.validation.service', [])
  .factory('cronValidationService', function() {
    function validate(expression) {
      let segments = expression.split(' ');
      // ignore the last segment (year) if it's '*', since it just clutters up the description
      if (segments.length === 7 && segments[6] === '*') {
        segments.pop();
        expression = segments.join(' ');
      }
      return API.one('cron')
        .one('validate')
        .withParams({ expression: expression }, {})
        .get();
    }

    return {
      validate: validate,
    };
  });
