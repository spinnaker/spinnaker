'use strict';

import * as angular from 'angular';

export const DCOS_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_CONTROLLER =
  'spinnaker.dcos.serverGroup.configure.basicSettings';
export const name = DCOS_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_CONTROLLER; // for backwards compatibility
angular
  .module(DCOS_SERVERGROUP_CONFIGURE_WIZARD_BASICSETTINGS_CONTROLLER, [])
  .controller('dcosServerGroupBasicSettingsController', [
    '$scope',
    '$controller',
    '$uibModalStack',
    '$state',
    'dcosImageReader',
    function ($scope, $controller, $uibModalStack, $state, dcosImageReader) {
      angular.extend(
        this,
        $controller('BasicSettingsMixin', {
          $scope: $scope,
          imageReader: dcosImageReader,
          $uibModalStack: $uibModalStack,
          $state: $state,
        }),
      );

      this.regionPattern = {
        test: function (stack) {
          const pattern = $scope.command.viewState.templatingEnabled
            ? /^((\/?((\.{2})|([a-z0-9][a-z0-9\-.]*[a-z0-9]+)|([a-z0-9]*))($|\/))*(\${.+})*)*$/
            : /^(\/?((\.{2})|([a-z0-9][a-z0-9\-.]*[a-z0-9]+)|([a-z0-9]*))($|\/))+$/;
          return pattern.test(stack);
        },
      };

      this.stackPattern = {
        test: function (stack) {
          const pattern = $scope.command.viewState.templatingEnabled ? /^([a-z0-9]*(\${.+})*)*$/ : /^[a-z0-9]*$/;
          return pattern.test(stack);
        },
      };

      this.detailPattern = {
        test: function (detail) {
          const pattern = $scope.command.viewState.templatingEnabled ? /^([a-z0-9-]*(\${.+})*)*$/ : /^[a-z0-9-]*$/;
          return pattern.test(detail);
        },
      };
    },
  ]);
