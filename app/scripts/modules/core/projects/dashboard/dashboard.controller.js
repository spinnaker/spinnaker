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
  .controller('ProjectDashboardCtrl', function ($scope, $q, projectConfiguration, executionService, projectReader,
                                                scheduler, recentHistoryService) {

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
      executionsLoaded: false,
      clustersLoaded: false,
      refreshing: false,
      lastRefresh: new Date().getTime(),
    };

    let getClusters = () => {
      return projectReader.getProjectClusters(projectConfiguration.name).then((clusters) => {
        this.clusters = clusters;
        this.state.clustersLoaded = true;
      });
    };

    let getExecutions = () => {
      return executionService.getProjectExecutions(projectConfiguration.name).then((executions) => {
        $scope.executions = executions;
        this.state.executionsLoaded = true;
      });
    };

    let dataLoader = scheduler.subscribe(() => {
      this.state.refreshing = true;
      $q.all([getClusters(), getExecutions()]).then(() => {
        this.state.refreshing = false;
        this.state.lastRefresh = new Date().getTime();
      });
    });

    $scope.$on('$destroy', () => dataLoader.dispose());

    this.refreshImmediately = scheduler.scheduleImmediate;

    this.refreshImmediately();

  });
