'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.delivery.execution.controller', [
  require('angular-ui-router'),
  require('../confirmationModal/confirmationModal.service.js'),
  require('./executionsService.js'),
])
  .controller('execution', function($scope, $stateParams, $state, confirmationModalService, executionsService, settings) {
    var controller = this;

    controller.showDetails = function() {
      return $scope.execution.id === $stateParams.executionId &&
        $state.includes('**.execution.**');
    };

    controller.pipelinesUrl = [settings.gateUrl, 'pipelines/'].join('/');

    controller.executionInState = function() {
      return $stateParams.executionId && $state.includes('**.execution.**');
    };

    controller.executionIsCurrent = function() {
      return controller.executionInState() && $scope.execution.id === $stateParams.executionId;
    };

    controller.deleteExecution = function() {
      confirmationModalService.confirm({
        header: 'Really delete execution?',
        buttonText: 'Delete',
        body: '<p>This will permanently delete the execution history.</p>',
        submitMethod: function() {
          return executionsService.deleteExecution($scope.application, $scope.execution.id);
        }
      });
    };

    controller.cancelExecution = function(execution) {
      confirmationModalService.confirm({
        header: 'Really stop execution of ' + execution.name + '?',
        buttonText: 'Stop running ' + execution.name,
        destructive: false,
        submitMethod: function() {
          return executionsService.cancelExecution(execution.id);
        }
      });

    };

  })
  .name;

