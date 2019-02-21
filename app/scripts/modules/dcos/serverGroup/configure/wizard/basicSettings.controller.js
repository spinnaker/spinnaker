'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.basicSettings', [])
  .controller('dcosServerGroupBasicSettingsController', ['$scope', '$controller', '$uibModalStack', '$state', 'dcosImageReader', function(
    $scope,
    $controller,
    $uibModalStack,
    $state,
    dcosImageReader,
  ) {
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
  }]);
