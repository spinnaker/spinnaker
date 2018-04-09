'use strict';

import { NAMING_SERVICE } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.basicSettings', [NAMING_SERVICE])
  .controller('dcosServerGroupBasicSettingsController', function(
    $scope,
    $controller,
    $uibModalStack,
    $state,
    dcosImageReader,
    namingService,
  ) {
    angular.extend(
      this,
      $controller('BasicSettingsMixin', {
        $scope: $scope,
        imageReader: dcosImageReader,
        namingService: namingService,
        $uibModalStack: $uibModalStack,
        $state: $state,
      }),
    );

    this.regionPattern = {
      test: function(stack) {
        var pattern = $scope.command.viewState.templatingEnabled
          ? /^((\/?((\.{2})|([a-z0-9][a-z0-9\-.]*[a-z0-9]+)|([a-z0-9]*))($|\/))*(\${.+})*)*$/
          : /^(\/?((\.{2})|([a-z0-9][a-z0-9\-.]*[a-z0-9]+)|([a-z0-9]*))($|\/))+$/;
        return pattern.test(stack);
      },
    };

    this.stackPattern = {
      test: function(stack) {
        var pattern = $scope.command.viewState.templatingEnabled ? /^([a-z0-9]*(\${.+})*)*$/ : /^[a-z0-9]*$/;
        return pattern.test(stack);
      },
    };

    this.detailPattern = {
      test: function(detail) {
        var pattern = $scope.command.viewState.templatingEnabled ? /^([a-z0-9-]*(\${.+})*)*$/ : /^[a-z0-9-]*$/;
        return pattern.test(detail);
      },
    };
  });
