'use strict';

import {INSTANCE_READ_SERVICE} from 'core/instance/instance.read.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.details.console.controller', [
  INSTANCE_READ_SERVICE,
])
  .controller('ConsoleOutputCtrl', function($scope, $uibModalInstance, instanceReader, instance) {
    $scope.vm = {
      loading: true,
      instanceId: instance.instanceId,
    };

    instanceReader.getConsoleOutput(instance.account, instance.region, instance.instanceId, instance.provider).then(
      function(response) {
        $scope.vm.consoleOutput = response.output;
        $scope.vm.loading = false;
      },
      function(exception) {
        $scope.vm.exception = exception;
        $scope.vm.loading = false;
      }
    );

    $scope.close = $uibModalInstance.dismiss;

  });
