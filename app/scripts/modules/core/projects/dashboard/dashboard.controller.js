'use strict';

let angular = require('angular');

require('./dashboard.less');

module.exports = angular.module('spinnaker.core.projects.dashboard.controller', [
  require('./cluster/projectCluster.directive.js'),
  require('./pipeline/projectPipeline.directive.js'),
  require('../service/project.read.service.js'),
  require('../../delivery/service/execution.service.js'),
  require('../../scheduler/scheduler.factory.js'),
  require('../../history/recentHistory.service.js'),
  require('../../presentation/refresher/componentRefresher.directive.js'),
  require('../../utils/lodash'),
  require('./regionFilter/regionFilter.component.js'),
  require('./regionFilter/regionFilter.service.js'),
])
  .controller('ProjectDashboardCtrl', function ($scope, $rootScope, projectConfiguration,
                                                executionService, projectReader, _, regionFilterService,
                                                schedulerFactory, recentHistoryService, $q) {

    this.project = projectConfiguration;

    // These templates are almost identical, but it doesn't look like you can pass in a directive easily as a tooltip so
    // here they are
    this.clusterRefreshTooltipTemplate = require('./clusterRefresh.tooltip.html');
    this.executionRefreshTooltipTemplate = require('./executionRefresh.tooltip.html');

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
      executions: {
        initializing: true,
        refreshing: false,
        loaded: false,
        error: false,
      },
      clusters: {
        initializing: true,
        refreshing: false,
        loaded: false,
        error: false
      },
    };

    let getClusters = () => {
      let state = this.state.clusters;
      state.error = false;
      state.refreshing = true;

      let clusterCount = _.get(projectConfiguration.config.clusters, 'length');
      let clustersPromise;

      if (clusterCount > 0) {
        clustersPromise = projectReader.getProjectClusters(projectConfiguration.name);
      } else if (clusterCount === 0) {
        clustersPromise = $q.when([]);
      } else { // shouldn't hide error if clusterCount is somehow undefined.
        clustersPromise = $q.reject(null);
      }

      return clustersPromise.then((clusters) => {
        this.clusters = clusters;
        this.allRegions = getAllRegions(clusters);
        state.initializing = false;
        state.loaded = true;
        state.refreshing = false;
        state.lastRefresh = new Date().getTime();
      }).catch(() => {
        state.initializing = false;
        state.refreshing = false;
        state.error = true;
      });
    };

    let getExecutions = () => {
      let state = this.state.executions;
      state.error = false;
      state.refreshing = true;
      return executionService.getProjectExecutions(projectConfiguration.name).then((executions) => {
        this.executions = executions;
        state.initializing = false;
        state.loaded = true;
        state.refreshing = false;
        state.lastRefresh = new Date().getTime();
        regionFilterService.activate();
        regionFilterService.runCallbacks();
      }).catch(() => {
        state.initializing = false;
        state.refreshing = false;
        state.error = true;
      });
    };

    let getAllRegions = (clusters) => {
      return _(clusters)
        .pluck('applications')
        .flatten()
        .pluck('clusters')
        .flatten()
        .pluck('region')
        .uniq()
        .valueOf();
    };

    let clusterScheduler = schedulerFactory.createScheduler(),
        executionScheduler = schedulerFactory.createScheduler();

    let clusterLoader = clusterScheduler.subscribe(getClusters);

    let executionLoader = executionScheduler.subscribe(getExecutions);

    $scope.$on('$destroy', () => {
      clusterScheduler.dispose();
      clusterLoader.dispose();

      executionScheduler.dispose();
      executionLoader.dispose();
    });

    this.refreshClusters = clusterScheduler.scheduleImmediate;
    this.refreshExecutions = executionScheduler.scheduleImmediate;

    this.refreshClusters();
    this.refreshExecutions();

    $scope.$on('$destroy', $rootScope.$on('$locationChangeSuccess', () => {
      regionFilterService.activate();
      regionFilterService.runCallbacks();
    }));
  });
