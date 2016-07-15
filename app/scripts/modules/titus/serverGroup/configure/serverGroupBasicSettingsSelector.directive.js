'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.titus.basicSettingsSelector', [
])
  .directive('titusServerGroupBasicSettingsSelector', function() {
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
  .controller('titusServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, namingService, $uibModalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));
  });
