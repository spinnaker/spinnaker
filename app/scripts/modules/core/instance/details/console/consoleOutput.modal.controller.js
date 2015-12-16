'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.details.console.controller', [
  require('../../instance.read.service.js'),
])
  .controller('ConsoleOutputCtrl', function($scope, $modalInstance, instanceReader, instance) {
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

    $scope.close = $modalInstance.dismiss;

  });
