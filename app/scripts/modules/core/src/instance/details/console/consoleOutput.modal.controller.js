'use strict';

import { InstanceReader } from '../../InstanceReader';

import { module } from 'angular';

export const CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER =
  'spinnaker.core.instance.details.console.controller';
export const name = CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER; // for backwards compatibility
module(CORE_INSTANCE_DETAILS_CONSOLE_CONSOLEOUTPUT_MODAL_CONTROLLER, []).controller('ConsoleOutputCtrl', [
  '$scope',
  '$uibModalInstance',
  'instance',
  'usesMultiOutput',
  function($scope, $uibModalInstance, instance, usesMultiOutput) {
    const instanceId = instance.instanceId || instance.id;
    $scope.vm = {
      loading: true,
      instanceId: instanceId,
      usesMultiOutput,
    };

    $scope.fetchLogs = isInitialFetch => {
      $scope.vm.loading = true;
      $scope.vm.exception = null;
      InstanceReader.getConsoleOutput(instance.account, instance.region, instanceId, instance.provider).then(
        function(response) {
          $scope.vm.consoleOutput = response.output;
          $scope.vm.loading = false;

          if ($scope.vm.usesMultiOutput) {
            $scope.selectLog = function(log) {
              $scope.vm.selectedLog = log;
            };
            if (isInitialFetch) {
              $scope.selectLog($scope.vm.consoleOutput[0]);
            }
          }
        },
        function(exception) {
          $scope.vm.exception = exception;
          $scope.vm.loading = false;
        },
      );
    };

    $scope.close = $uibModalInstance.dismiss;

    $scope.jumpToEnd = () => {
      const console = document.getElementById('console-output');
      console.scrollTop = console.scrollHeight;
    };

    $scope.fetchLogs(true);
  },
]);
