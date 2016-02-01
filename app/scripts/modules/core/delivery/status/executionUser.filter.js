'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionUser.filter', [
  require('../../utils/lodash.js'),
])
  .filter('executionUser', function (_) {
    return function (input) {
      if (!input.trigger.user) {
        return 'unknown user';
      }
      var user = input.trigger.user;
      if (user === '[anonymous]' && _.has(input, 'trigger.parentExecution.trigger.user')) {
        user = input.trigger.parentExecution.trigger.user;
      }
      return user;
    };
  });
