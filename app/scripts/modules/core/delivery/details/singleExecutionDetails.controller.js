'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.singleExecutionDetails.controller', [
    require('angular-ui-router'),
    require('../service/execution.service.js'),
    require('../../scheduler/scheduler.factory.js'),

  ])
  .controller('SingleExecutionDetailsCtrl', function ($scope, $state, executionService, schedulerFactory) {

    let executionScheduler = schedulerFactory.createScheduler(5000);

    let getExecution = () => {
      let application = $scope.application;
      this.application = application;
      if ($scope.application.notFound) {
        return;
      }
      executionService.getExecution($state.params.executionId).then((execution) =>
      {
        this.execution = execution;
        executionService.transformExecution(this.application, this.execution);
        if (!execution.isActive) {
          executionScheduler.dispose();
          executionLoader.dispose();
        }
      }, () => {
        this.execution = null;
        this.stateNotFound = true;
      });
    };

    let executionLoader = executionScheduler.subscribe(getExecution);
    getExecution();

    $scope.$on('$destroy', () => {
      executionScheduler.dispose();
      executionLoader.dispose();
    });

    this.showDetails = () => true;

    $scope.$on('$stateChangeSuccess', (event, toState, toParams, fromState, fromParams) => {
      if (toParams.application !== fromParams.application || toParams.executionId !== fromParams.executionId) {
        getExecution();
      }
    });
  });
