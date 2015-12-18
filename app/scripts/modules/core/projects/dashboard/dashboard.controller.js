'use strict';

let angular = require('angular');

require('./dashboard.less');

module.exports = angular.module('spinnaker.core.projects.dashboard.controller', [
  require('./cluster/projectCluster.directive.js'),
  require('./pipeline/projectPipeline.directive.js'),
  require('../../delivery/service/execution.service.js'),
  require('../../scheduler/scheduler.service.js'),
  require('../../history/recentHistory.service.js'),
])
  .controller('ProjectDashboardCtrl', function ($scope, projectConfiguration, executionService, scheduler, recentHistoryService) {

    $scope.project = projectConfiguration;

    this.refreshTooltipTemplate = require('./dashboardRefresh.tooltip.html');

    if (projectConfiguration.notFound) {
      recentHistoryService.removeLastItem('projects');
      return;
    } else {
      recentHistoryService.addExtraDataToLatest('projects',
        {
          config: {
            applications: projectConfiguration.config.applications
          }
        });
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

  });
