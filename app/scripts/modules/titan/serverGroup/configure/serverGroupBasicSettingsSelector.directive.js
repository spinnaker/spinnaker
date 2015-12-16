'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titan.basicSettingsSelector', [
])
  .directive('titanServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsDirective.html'),
      controller: 'titanServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('titanServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, namingService, $uibModalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  });
