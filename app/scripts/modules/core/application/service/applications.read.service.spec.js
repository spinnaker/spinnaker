'use strict';

describe('Service: applicationReader', function () {

  var applicationReader;
  var securityGroupReader;
  var loadBalancerReader;
  var clusterService;
  var executionService;
  var pipelineConfigService;
  var taskReader;
  var $http;
  var $q;
  var $scope;
  var settings;

  beforeEach(
    window.module(
      require('./applications.read.service')
    )
  );

  beforeEach(
    window.inject(function (_applicationReader_, _securityGroupReader_, _clusterService_, $httpBackend, _$q_,
                            _loadBalancerReader_, _executionService_, _taskReader_, _pipelineConfigService_,
                            $rootScope, _settings_) {
      applicationReader = _applicationReader_;
      securityGroupReader = _securityGroupReader_;
      clusterService = _clusterService_;
      loadBalancerReader = _loadBalancerReader_;
      $http = $httpBackend;
      executionService = _executionService_;
      pipelineConfigService = _pipelineConfigService_;
      taskReader = _taskReader_;
      $q = _$q_;
      $scope = $rootScope.$new();
      settings = _settings_;
    })
  );

  describe('load application', function () {

    function loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName, options) {
      $http.expectGET(settings.gateUrl + '/applications/deck').respond(200, {name: 'deck', attributes: {}});
      spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue($q.when(securityGroupsByApplicationName));
      spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue($q.when(loadBalancers));
      spyOn(clusterService, 'loadServerGroups').and.returnValue($q.when(serverGroups));
      spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'attachSecurityGroups').and.callFake(function(app, groupsByName) {
        if (groupsByName) {
          app.securityGroups.data = groupsByName;
        }
        return $q.when(app);
      });

      return applicationReader.getApplication('deck', options).then(app => {
        if (options) {
          Object.keys(options).forEach(option => {
            app[option].activate();
          });
        }
        return app;
      });
    }

    describe('lazy sections', function () {
      describe('activate', function () {
        it('refreshes section if not already active and not already loaded', function () {
          spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
          spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
          var app = null;
          loadApplication([], [], {}).then(result => app = result);
          $scope.$digest();
          $http.flush();
          spyOn(app.executions, 'refresh').and.callFake(() => app.executions.loaded = true);
          app.executions.activate();
          expect(app.executions.refresh.calls.count()).toBe(1);
          expect(app.executions.active).toBe(true);
          expect(app.executions.loaded).toBe(true);

          app.executions.deactivate();
          expect(app.executions.active).toBe(false);
          app.executions.activate();
          // not refreshed since still loaded
          expect(app.executions.active).toBe(true);
          expect(app.executions.refresh.calls.count()).toBe(1);

          app.executions.deactivate();
          app.executions.loaded = false;
          app.executions.activate();
          expect(app.executions.refresh.calls.count()).toBe(2);
        });
      });
      describe('refresh behavior', function () {
        it('clears data on inactive lazy sections and sets loaded flag to false', function () {
          spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
          spyOn(executionService, 'getExecutions').and.returnValue($q.when([{status: 'SUCCEEDED', stages: []}]));
          spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
          var app = null;
          loadApplication([], [], [], {}).then(result => app = result);
          $scope.$digest();
          $http.flush();

          expect(app.executions.active).toBeFalsy();

          app.executions.activate();
          $scope.$digest();
          expect(app.executions.active).toBe(true);
          expect(app.executions.loaded).toBe(true);
          expect(app.executions.data.length).toBe(1);

          app.executions.deactivate();
          app.refresh();
          $scope.$digest();

          expect(app.executions.data).toEqual([]);
          expect(app.executions.loaded).toBe(false);
        });
      });

      describe('application ready', function () {
        it('ignores lazy sections when determining if application is ready', function () {
          spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
          spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));

          var app = null,
              isReady = false;
          loadApplication([], [], [], {}).then(result => app = result);
          $scope.$digest();
          $http.flush();

          app.ready().then(() => isReady = true);
          $scope.$digest();
          expect(isReady).toBe(true);
        });
      });
    });

    describe('loading executions', function () {
      beforeEach(function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
        spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
      });

      it('loads executions and sets appropriate flags', function () {
        spyOn(executionService, 'getExecutions').and.returnValue($q.when([{status: 'SUCCEEDED', stages: []}]));
        var result = null;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.executions.loaded).toBe(true);
        expect(result.executions.loading).toBe(false);
        expect(result.executions.loadFailure).toBe(false);
      });

      it('sets appropriate flags when execution load fails', function () {
        spyOn(executionService, 'getExecutions').and.returnValue($q.reject(null));
        var result = null;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.executions.loaded).toBe(false);
        expect(result.executions.loading).toBe(false);
        expect(result.executions.loadFailure).toBe(true);
      });
    });

    describe('reload executions', function () {
      beforeEach(function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
        spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
      });

      it('reloads executions and sets appropriate flags', function () {
        spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
        var result = null;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.executions.loaded).toBe(true);
        expect(result.executions.loading).toBe(false);
        expect(result.executions.loadFailure).toBe(false);

        result.executions.refresh();
        expect(result.executions.loading).toBe(true);

        $scope.$digest();
        expect(result.executions.loaded).toBe(true);
        expect(result.executions.loading).toBe(false);
        expect(result.executions.loadFailure).toBe(false);

      });

      it('sets appropriate flags when executions reload fails; subscriber is responsible for error checking', function () {
        spyOn(executionService, 'getExecutions').and.returnValue($q.reject(null));
        var result = null,
            errorsHandled = 0,
            successesHandled = 0;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();

        result.executions.onRefresh($scope, () => successesHandled++, () => errorsHandled++);

        result.executions.refresh();
        $scope.$digest();

        expect(result.executions.loading).toBe(false);
        expect(result.executions.loadFailure).toBe(true);

        result.executions.refresh();
        $scope.$digest();

        expect(successesHandled).toBe(0);
        expect(errorsHandled).toBe(2);
      });
    });

    describe('loading tasks', function () {
      beforeEach(function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
        spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
      });

      it('loads tasks and sets appropriate flags', function () {
        spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
        var result = null;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.tasks.loaded).toBe(true);
        expect(result.tasks.loading).toBe(false);
        expect(result.tasks.loadFailure).toBe(false);
      });

      it('sets appropriate flags when task load fails', function () {
        spyOn(taskReader, 'getTasks').and.returnValue($q.reject(null));
        var result = null;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.tasks.loaded).toBe(false);
        expect(result.tasks.loading).toBe(false);
        expect(result.tasks.loadFailure).toBe(true);
      });
    });

    describe('reload tasks', function () {
      beforeEach(function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
        spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
      });

      it('reloads tasks and sets appropriate flags', function () {
        spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
        var result = null,
            nextCalls = 0;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        result.tasks.onRefresh($scope, () => nextCalls++);
        expect(result.tasks.loaded).toBe(true);
        expect(result.tasks.loading).toBe(false);
        expect(result.tasks.loadFailure).toBe(false);

        result.tasks.refresh();
        expect(result.tasks.loading).toBe(true);

        $scope.$digest();
        expect(result.tasks.loaded).toBe(true);
        expect(result.tasks.loading).toBe(false);
        expect(result.tasks.loadFailure).toBe(false);

        expect(nextCalls).toBe(1);
      });

      it('sets appropriate flags when task reload fails; subscriber is responsible for error checking', function () {
        spyOn(taskReader, 'getTasks').and.returnValue($q.reject(null));
        var result = null,
            errorsHandled = 0,
            successesHandled = 0;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        result.tasks.onRefresh($scope, () => successesHandled++, () => errorsHandled++);

        result.tasks.refresh();
        $scope.$digest();

        expect(result.tasks.loading).toBe(false);
        expect(result.tasks.loadFailure).toBe(true);

        result.tasks.refresh();
        $scope.$digest();

        expect(errorsHandled).toBe(2);
        expect(successesHandled).toBe(0);
      });
    });

    describe('loading pipeline configs', function () {
      beforeEach(function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
        spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
        spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
      });

      it('loads configs and sets appropriate flags', function () {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
        var result = null;
        loadApplication([], [], [], {pipelineConfigs: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();

        result.pipelineConfigs.refresh();
        expect(result.pipelineConfigs.loading).toBe(true);
        $scope.$digest();
        expect(result.pipelineConfigs.loaded).toBe(true);
        expect(result.pipelineConfigs.loading).toBe(false);
        expect(result.pipelineConfigs.loadFailure).toBe(false);
      });

      it('sets appropriate flags when pipeline config reload fails; subscriber is responsible for error checking', function () {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.reject([]));
        var result = null,
            errorsHandled = 0,
            successesHandled = 0;
        loadApplication([], [], [], {pipelineConfigs: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();

        result.pipelineConfigs.onRefresh($scope, () => successesHandled++, () => errorsHandled++);
        result.pipelineConfigs.refresh();
        $scope.$digest();

        expect(result.pipelineConfigs.loading).toBe(false);
        expect(result.pipelineConfigs.loadFailure).toBe(true);

        result.pipelineConfigs.refresh();
        $scope.$digest();

        expect(errorsHandled).toBe(2);
        expect(successesHandled).toBe(0);
      });
    });

    describe('setting default credentials and regions', function () {
      beforeEach(function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([]));
        spyOn(executionService, 'getExecutions').and.returnValue($q.when([]));
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([]));
        spyOn(taskReader, 'getTasks').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
      });

      it('sets default credentials and region from server group when only one account/region found', function () {
        var serverGroups = [{
              name: 'deck-test-v001',
              cluster: 'deck-test',
              account: 'test',
              region: 'us-west-2',
              provider: 'aws',
              instances: [],
              instanceCounts: { up: 0, down: 0, starting: 0, unknown: 0, outOfService: 0 },
            }],
            loadBalancers = [],
            securityGroupsByApplicationName = [],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.aws).toBe('test');
        expect(result.defaultRegions.aws).toBe('us-west-2');
      });

      it('sets default credentials and region from load balancer when only one account/region found', function () {
        var serverGroups = [],
            loadBalancers = [{name: 'deck-frontend', account: 'prod', type: 'gce', region: 'us-central-1', serverGroups: []}],
            securityGroupsByApplicationName = [],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.gce).toBe('prod');
        expect(result.defaultRegions.gce).toBe('us-central-1');
      });

      it('sets default credentials and region from security group', function () {
        var serverGroups = [],
            loadBalancers = [],
            securityGroupsByApplicationName = [{name: 'deck-test', type: 'cf', accountName: 'test', region: 'us-south-7'}],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.cf).toBe('test');
        expect(result.defaultRegions.cf).toBe('us-south-7');
      });

      it('does not set defaults when multiple values found for the same provider', function () {
        var serverGroups = [],
            loadBalancers = [{name: 'deck-frontend', account: 'prod', type: 'aws', region: 'us-west-1', serverGroups: []}],
            securityGroupsByApplicationName = [{name: 'deck-test', type: 'aws', accountName: 'test', region: 'us-east-1'}],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.aws).toBeUndefined();
        expect(result.defaultRegions.aws).toBeUndefined();
      });

      it('sets default region or default credentials if possible', function () {
        var serverGroups = [],
            loadBalancers = [{name: 'deck-frontend', account: 'prod', type: 'aws', region: 'us-east-1', serverGroups: []}],
            securityGroupsByApplicationName = [{name: 'deck-test', type: 'aws', accountName: 'test', region: 'us-east-1'}],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.aws).toBeUndefined();
        expect(result.defaultRegions.aws).toBe('us-east-1');
      });

      it('sets default credentials, even if region cannot be set', function () {
        var serverGroups = [],
            loadBalancers = [{name: 'deck-frontend', account: 'test', type: 'aws', region: 'us-east-1', serverGroups: []}],
            securityGroupsByApplicationName = [{name: 'deck-test', type: 'aws', accountName: 'test', region: 'us-west-1'}],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.aws).toBe('test');
        expect(result.defaultRegions.aws).toBeUndefined();
      });

      it('should set defaults for multiple providers', function () {
        var serverGroups = [
              {
                name: 'deck-test-v001',
                account: 'test',
                region: 'us-west-2',
                provider: 'aws',
                instances: [],
                instanceCounts: { up: 0, down: 0, starting: 0, unknown: 0, outOfService: 0 },
              },
              {
                name: 'deck-gce-v001',
                account: 'gce-test',
                region: 'us-central-1',
                provider: 'gce',
                instances: [],
                instanceCounts: { up: 0, down: 0, starting: 0, unknown: 0, outOfService: 0 },
              }
            ],
            loadBalancers = [{name: 'deck-frontend', account: 'gce-test', type: 'gce', region: 'us-central-1', serverGroups: []}],
            securityGroupsByApplicationName = [{name: 'deck-test', type: 'aws', accountName: 'test', region: 'us-west-2'}],
            result = null;

        loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.defaultCredentials.aws).toBe('test');
        expect(result.defaultRegions.aws).toBe('us-west-2');
        expect(result.defaultCredentials.gce).toBe('gce-test');
        expect(result.defaultRegions.gce).toBe('us-central-1');
      });
    });
  });

});
