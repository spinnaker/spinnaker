'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { CORE_PROJECTS_DASHBOARD_CLUSTER_PROJECTCLUSTER_DIRECTIVE } from './cluster/projectCluster.directive';
import { RecentHistoryService } from '../../history/recentHistory.service';
import { PROJECT_PIPELINE_COMPONENT } from './pipeline/projectPipeline.component';
import { EXECUTION_SERVICE } from '../../pipeline/service/execution.service';
import { CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT } from './regionFilter/regionFilter.component';
import { CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE } from './regionFilter/regionFilter.service';
import { SchedulerFactory } from '../../scheduler/SchedulerFactory';
import { ProjectReader } from '../service/ProjectReader';

import './dashboard.less';

export const CORE_PROJECTS_DASHBOARD_DASHBOARD_CONTROLLER = 'spinnaker.core.projects.dashboard.controller';
export const name = CORE_PROJECTS_DASHBOARD_DASHBOARD_CONTROLLER; // for backwards compatibility
module(CORE_PROJECTS_DASHBOARD_DASHBOARD_CONTROLLER, [
  CORE_PROJECTS_DASHBOARD_CLUSTER_PROJECTCLUSTER_DIRECTIVE,
  PROJECT_PIPELINE_COMPONENT,
  EXECUTION_SERVICE,
  CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_COMPONENT,
  CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE,
]).controller('ProjectDashboardCtrl', [
  '$scope',
  '$rootScope',
  'projectConfiguration',
  'executionService',
  'regionFilterService',
  '$q',
  function ($scope, $rootScope, projectConfiguration, executionService, regionFilterService, $q) {
    this.project = projectConfiguration;
    this.application = ApplicationModelBuilder.createStandaloneApplication('project');

    // These templates are almost identical, but it doesn't look like you can pass in a directive easily as a tooltip so
    // here they are
    this.clusterRefreshTooltipTemplate = require('./clusterRefresh.tooltip.html');
    this.executionRefreshTooltipTemplate = require('./executionRefresh.tooltip.html');

    if (projectConfiguration.notFound) {
      RecentHistoryService.removeLastItem('projects');
      return;
    } else {
      RecentHistoryService.addExtraDataToLatest('projects', {
        config: {
          applications: projectConfiguration.config.applications,
        },
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
        error: false,
      },
    };

    const getClusters = () => {
      const state = this.state.clusters;
      state.error = false;
      state.refreshing = true;

      const clusterCount = _.get(projectConfiguration.config.clusters, 'length');
      let clustersPromise;

      if (clusterCount > 0) {
        clustersPromise = ProjectReader.getProjectClusters(projectConfiguration.name);
      } else if (clusterCount === 0) {
        clustersPromise = $q.when([]);
      } else {
        // shouldn't hide error if clusterCount is somehow undefined.
        clustersPromise = $q.reject(null);
      }

      return clustersPromise
        .then((clusters) => {
          this.clusters = clusters;
          this.allRegions = getAllRegions(clusters);
          state.initializing = false;
          state.loaded = true;
          state.refreshing = false;
          state.lastRefresh = new Date().getTime();
        })
        .catch(() => {
          state.initializing = false;
          state.refreshing = false;
          state.error = true;
        });
    };

    const getExecutions = () => {
      const state = this.state.executions;
      state.error = false;
      state.refreshing = true;
      return executionService
        .getProjectExecutions(projectConfiguration.name)
        .then((executions) => {
          this.executions = executions;
          state.initializing = false;
          state.loaded = true;
          state.refreshing = false;
          state.lastRefresh = new Date().getTime();
          regionFilterService.activate();
          regionFilterService.runCallbacks();
        })
        .catch(() => {
          state.initializing = false;
          state.refreshing = false;
          state.error = true;
        });
    };

    const getAllRegions = (clusters) => {
      return _.chain(clusters).map('applications').flatten().map('clusters').flatten().map('region').uniq().value();
    };

    const clusterScheduler = SchedulerFactory.createScheduler(3 * 60 * 1000);
    const executionScheduler = SchedulerFactory.createScheduler(3 * 60 * 1000);

    const clusterLoader = clusterScheduler.subscribe(getClusters);

    const executionLoader = executionScheduler.subscribe(getExecutions);

    $scope.$on('$destroy', () => {
      clusterScheduler.unsubscribe();
      clusterLoader.unsubscribe();

      executionScheduler.unsubscribe();
      executionLoader.unsubscribe();
    });

    this.refreshClusters = clusterScheduler.scheduleImmediate;
    this.refreshExecutions = executionScheduler.scheduleImmediate;

    this.refreshClusters();
    this.refreshExecutions();

    $scope.$on(
      '$destroy',
      $rootScope.$on('$locationChangeSuccess', () => {
        regionFilterService.activate();
        regionFilterService.runCallbacks();
      }),
    );
  },
]);
