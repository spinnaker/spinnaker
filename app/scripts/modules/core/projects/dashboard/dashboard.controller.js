'use strict';

let angular = require('angular');

require('./dashboard.less');

module.exports = angular.module('spinnaker.core.projects.dashboard.controller', [
  require('./cluster/projectCluster.directive.js'),
  require('./pipeline/projectPipeline.directive.js'),
  require('../../delivery/execution.service.js'),
  require('../../scheduler/scheduler.service.js'),
])
  .controller('ProjectDashboardCtrl', function ($scope, projectConfiguration, executionService, scheduler) {

    $scope.project = projectConfiguration;

    if (projectConfiguration.notFound) {
      return;
    }

    this.state = {
      refreshing: false,
      lastRefresh: new Date().getTime(),
    };

    let getExecutions = () => {
      this.state.refreshing = true;
      executionService.getProjectExecutions(projectConfiguration.name).then((executions) => {
        $scope.executions = executions;
        this.state.refreshing = false;
        this.state.lastRefresh = new Date().getTime();
      });
    };

    let dataLoader = scheduler.subscribe(getExecutions);

    $scope.$on('$destroy', () => dataLoader.dispose());

    this.refreshImmediately = scheduler.scheduleImmediate;

    this.refreshImmediately();

  }).name;
