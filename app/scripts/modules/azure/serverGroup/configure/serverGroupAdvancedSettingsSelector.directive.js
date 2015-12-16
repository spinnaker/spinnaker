'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.advancedSettings', [])
  .directive('azureServerGroupAdvancedSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./serverGroupAdvancedSettingsSelector.directive.html'),
      controller: 'azureServerGroupAdvancedSettingsSelectorCtrl as advancedSettingsCtrl',
    };
  })
  .controller('azureServerGroupAdvancedSettingsSelectorCtrl', function($scope) {

    this.toggleSuspendedProcess = function(process) {
      $scope.command.suspendedProcesses = $scope.command.suspendedProcesses || [];
      var processIndex = $scope.command.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        $scope.command.suspendedProcesses.push(process);
      } else {
        $scope.command.suspendedProcesses.splice(processIndex, 1);
      }
    };

    this.processIsSuspended = function(process) {
      return $scope.command.suspendedProcesses.indexOf(process) !== -1;
    };

  });
