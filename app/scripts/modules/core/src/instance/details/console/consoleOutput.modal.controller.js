'use strict';

import { InstanceReader } from 'core/instance/InstanceReader';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.details.console.controller', [])
  .controller('ConsoleOutputCtrl', function($scope, $uibModalInstance, instance) {
    const instanceId = instance.instanceId || instance.id;
    $scope.vm = {
      loading: true,
      instanceId: instanceId,
    };

    InstanceReader.getConsoleOutput(instance.account, instance.region, instanceId, instance.provider).then(
      function(response) {
        $scope.vm.consoleOutput = response.output;
        $scope.vm.loading = false;
      },
      function(exception) {
        $scope.vm.exception = exception;
        $scope.vm.loading = false;
      },
    );

    $scope.close = $uibModalInstance.dismiss;

    $scope.jumpToEnd = () => {
      const console = document.getElementById('console-output');
      console.scrollTop = console.scrollHeight;
    };
  });
