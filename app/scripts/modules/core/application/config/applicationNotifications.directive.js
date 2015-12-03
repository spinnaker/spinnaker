'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.notifications.directive', [

  ])
  .directive('applicationNotifications', function (overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate('applicationNotificationsDirective', require('./applicationNotifications.directive.html')),
      scope: {},
      bindToController: {
        application: '=',
        notifications: '='
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  }).name;
