'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.configure.titus.basicSettingsSelector', [
  ACCOUNT_SERVICE
])
  .directive('titusServerGroupBasicSettingsSelector', function () {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsDirective.html'),
      controller: 'titusServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('titusServerGroupBasicSettingsSelectorCtrl', function ($scope, $controller, namingService, $uibModalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    this.detailPattern = {
      test: function (detail) {
        var pattern = $scope.command.viewState.templatingEnabled ?
          /^([a-zA-Z_0-9._$-{}\\\^~]*(\${.+})*)*$/ :
          /^[a-zA-Z_0-9._$-{}\\\^~]*$/;

        return isNotExpressionLanguage(detail) ? pattern.test(detail) : true;
      }
    };

    let isNotExpressionLanguage = (field) => {
      return field && !field.includes('${');
    };

    function updateImageId() {
      if ($scope.command.repository && $scope.command.tag) {
        $scope.command.imageId = `${$scope.command.repository}:${$scope.command.tag}`;
      }
      else {
        delete $scope.command.imageId;
      }
    }

    if ($scope.command.imageId) {
      const image = $scope.command.imageId;
      $scope.command.organization = '';
      const parts = image.split('/');
      if (parts.length > 1) {
        $scope.command.organization = parts.shift();
      }

      const rest = parts.shift().split(':');
      if ($scope.command.organization) {
        $scope.command.repository = `${$scope.command.organization}/${rest.shift()}`;
      } else {
        $scope.command.repository = rest.shift();
      }
      $scope.command.tag = rest.shift();
    }

    $scope.$watchGroup(['command.repository', 'command.tag'], updateImageId);
  });
