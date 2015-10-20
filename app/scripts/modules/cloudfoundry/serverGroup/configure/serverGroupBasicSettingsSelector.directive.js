'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.basicSettingsSelector', [
])
  .directive('cfServerGroupBasicSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
        application: '=',
        hideClusterNamePreview: '=',
      },
      templateUrl: require('./serverGroupBasicSettingsDirective.html'),
      controller: 'cfServerGroupBasicSettingsSelectorCtrl as basicSettingsCtrl',
    };
  })
  .controller('cfServerGroupBasicSettingsSelectorCtrl', function($scope, $controller, RxService, imageReader, namingService, $uibModalStack, $state) {
    angular.extend(this, $controller('BasicSettingsMixin', {
      $scope: $scope,
      RxService: RxService,
      imageReader: imageReader,
      namingService: namingService,
      $uibModalStack: $uibModalStack,
      $state: $state,
    }));

    this.getApplication = function() {
      var command = $scope.command;
      return command.application;
    };


    })
.name;
