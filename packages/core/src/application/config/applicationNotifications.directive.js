'use strict';

import * as angular from 'angular';

import { OVERRIDE_REGISTRY } from '../../overrideRegistry/override.registry';

export const CORE_APPLICATION_CONFIG_APPLICATIONNOTIFICATIONS_DIRECTIVE =
  'spinnaker.core.application.config.notifications.directive';
export const name = CORE_APPLICATION_CONFIG_APPLICATIONNOTIFICATIONS_DIRECTIVE; // for backwards compatibility
angular
  .module(CORE_APPLICATION_CONFIG_APPLICATIONNOTIFICATIONS_DIRECTIVE, [OVERRIDE_REGISTRY])
  .directive('applicationNotifications', [
    'overrideRegistry',
    function (overrideRegistry) {
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
          updateNotifications: '=',
        },
        controllerAs: 'vm',
        controller: angular.noop,
      };
    },
  ]);
