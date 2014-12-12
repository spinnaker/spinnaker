'use strict';

angular.module('deckApp')
  .directive('serverGroupAdvancedSettingsSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'views/application/modal/serverGroup/aws/serverGroupAdvancedSettingsDirective.html',
      controller: 'ServerGroupAdvancedSettingsCtrl as advancedSettingsCtrl',
    }
  })
  .controller('ServerGroupAdvancedSettingsCtrl', function($scope) {
    $scope.healthCheckTypes = ['EC2', 'ELB'];
    $scope.terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

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
    }

  });
