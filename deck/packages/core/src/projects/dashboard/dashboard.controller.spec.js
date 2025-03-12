'use strict';

import { RecentHistoryService } from '../../history';
import { ProjectReader } from '../service/ProjectReader';

describe('Controller: Project Dashboard', function () {
  var executionService,
    projectConfig,
    vm,
    $q,
    $scope,
    clusters = [],
    executions = [];

  beforeEach(window.module(require('./dashboard.controller').name));

  beforeEach(
    window.inject(function ($controller, $rootScope, _executionService_, _$q_) {
      executionService = _executionService_;
      $q = _$q_;
      $scope = $rootScope.$new();
      projectConfig = { name: 'the project', config: { applications: ['a', 'b'], clusters: ['a'] } };

      this.initialize = () => {
        vm = $controller('ProjectDashboardCtrl', {
          $scope: $scope,
          executionService: executionService,
          projectConfiguration: projectConfig,
        });
      };
    }),
  );

  describe('recent history application', function () {
    it('adds project applications via recent history', function () {
      var historyType = null,
        appList = null;
      spyOn(RecentHistoryService, 'addExtraDataToLatest').and.callFake((type, project) => {
        historyType = type;
        appList = project.config.applications;
      });
      this.initialize();
      expect(historyType).toBe('projects');
      expect(appList).toBe(projectConfig.config.applications);
    });

    it('removes project from recent history if not found', function () {
      var historyType = null;
      projectConfig.notFound = true;
      spyOn(RecentHistoryService, 'removeLastItem').and.callFake((type) => (historyType = type));
      this.initialize();
      expect(historyType).toBe('projects');
    });
  });

  describe('initialization, no errors', function () {
    beforeEach(function () {
      spyOn(ProjectReader, 'getProjectClusters').and.callFake(() => $q.when(clusters));
      spyOn(executionService, 'getProjectExecutions').and.callFake(() => $q.when(executions));
    });

    it('loads clusters on initialization, sets state, then sets state when clusters finish loading', function () {
      this.initialize();

      let state = vm.state.clusters;
      expect(vm.clusters).toBeUndefined();
      expect(state.loaded).toBe(false);
      expect(state.initializing).toBe(true);
      expect(state.refreshing).toBe(true);
      expect(state.lastRefresh).toBeUndefined();

      $scope.$digest();
      expect(vm.clusters).toEqual([]);
      expect(state.loaded).toBe(true);
      expect(state.initializing).toBe(false);
      expect(state.refreshing).toBe(false);
      expect(state.lastRefresh).not.toBeUndefined();
    });

    it('loads executions on initialization, sets state, then sets state when executions finish loading', function () {
      this.initialize();

      let state = vm.state.executions;
      expect(vm.executions).toBeUndefined();
      expect(state.loaded).toBe(false);
      expect(state.initializing).toBe(true);
      expect(state.refreshing).toBe(true);
      expect(state.lastRefresh).toBeUndefined();

      $scope.$digest();
      expect(vm.executions).toEqual([]);
      expect(state.loaded).toBe(true);
      expect(state.initializing).toBe(false);
      expect(state.refreshing).toBe(false);
      expect(state.lastRefresh).not.toBeUndefined();
    });
  });

  describe('initialization with errors', function () {
    it('loads clusters as expected, but sets error flag when executions fail to load', function () {
      spyOn(ProjectReader, 'getProjectClusters').and.callFake(() => $q.when(clusters));
      spyOn(executionService, 'getProjectExecutions').and.callFake(() => $q.reject(null));

      this.initialize();

      let state = vm.state;
      $scope.$digest();

      expect(vm.clusters).toEqual([]);
      expect(state.clusters.loaded).toBe(true);
      expect(state.clusters.initializing).toBe(false);
      expect(state.clusters.refreshing).toBe(false);
      expect(state.clusters.lastRefresh).not.toBeUndefined();

      expect(vm.executions).toBeUndefined();
      expect(state.executions.error).toBe(true);
      expect(state.executions.loaded).toBe(false);
      expect(state.executions.initializing).toBe(false);
      expect(state.executions.refreshing).toBe(false);
      expect(state.executions.lastRefresh).toBeUndefined();
    });

    it('loads executions as expected, but sets error flag when clusters fail to load', function () {
      spyOn(ProjectReader, 'getProjectClusters').and.callFake(() => $q.reject(null));
      spyOn(executionService, 'getProjectExecutions').and.callFake(() => $q.when(executions));

      this.initialize();

      let state = vm.state;
      $scope.$digest();

      expect(vm.clusters).toBeUndefined();
      expect(state.clusters.error).toBe(true);
      expect(state.clusters.loaded).toBe(false);
      expect(state.clusters.initializing).toBe(false);
      expect(state.clusters.refreshing).toBe(false);
      expect(state.clusters.lastRefresh).toBeUndefined();

      expect(vm.executions).toEqual([]);
      expect(state.executions.loaded).toBe(true);
      expect(state.executions.initializing).toBe(false);
      expect(state.executions.refreshing).toBe(false);
      expect(state.executions.lastRefresh).not.toBeUndefined();
    });
  });
});
