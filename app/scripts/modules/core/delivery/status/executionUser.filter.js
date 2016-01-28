'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executionUser.filter', [])
  .filter('executionUser', function () {
    return function (input) {
      if (!input.trigger.user) {
        return 'unknown user';
      }
      var user = input.trigger.user;
      if (user === '[anonymous]') {
        user = input.trigger.parentExecution.trigger.user || user;
      }
      return user;
    };
  });
