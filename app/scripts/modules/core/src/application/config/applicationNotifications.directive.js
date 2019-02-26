'use strict';

const angular = require('angular');

import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

module.exports = angular
  .module('spinnaker.core.application.config.notifications.directive', [OVERRIDE_REGISTRY])
  .directive('applicationNotifications', [
    'overrideRegistry',
    function(overrideRegistry) {
      return {
        restrict: 'E',
        templateUrl: overrideRegistry.getTemplate(
          'applicationNotificationsDirective',
          require('./applicationNotifications.directive.html'),
        ),
        scope: {},
        bindToController: {
          application: '=',
          notifications: '=',
        },
        controllerAs: 'vm',
        controller: angular.noop,
      };
    },
  ]);
