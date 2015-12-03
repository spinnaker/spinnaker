'use strict';

describe('Service: applicationReader', function () {

  var applicationReader;
  var application;
  var securityGroupReader;
  var loadBalancerReader;
  var clusterService;
  var executionService;
  var pipelineConfigService;
  var taskReader;
  var $http;
  var $q;
  var $scope;

  beforeEach(
    window.module(
      require('./applications.read.service')
    )
  );

  beforeEach(
    window.inject(function (_applicationReader_, _securityGroupReader_, _clusterService_, $httpBackend, _$q_,
                            _loadBalancerReader_, _executionService_, _taskReader_, _pipelineConfigService_,
                            $rootScope) {
      applicationReader = _applicationReader_;
      securityGroupReader = _securityGroupReader_;
      clusterService = _clusterService_;
      loadBalancerReader = _loadBalancerReader_;
      $http = $httpBackend;
      executionService = _executionService_;
      pipelineConfigService = _pipelineConfigService_;
      taskReader = _taskReader_;
      $q = _$q_;
      application = {};
      $scope = $rootScope.$new();
    })
  );

  describe('load application', function () {

    function loadApplication(serverGroups, loadBalancers, securityGroupsByApplicationName, options) {
      $http.expectGET('/applications/deck').respond(200, {name: 'deck', attributes: {}});
      spyOn(securityGroupReader, 'loadSecurityGroupsByApplicationName').and.returnValue($q.when(securityGroupsByApplicationName));
      spyOn(loadBalancerReader, 'loadLoadBalancers').and.returnValue($q.when(loadBalancers));
      spyOn(clusterService, 'loadServerGroups').and.returnValue($q.when(serverGroups));
      spyOn(securityGroupReader, 'loadSecurityGroups').and.returnValue($q.when([]));
      spyOn(securityGroupReader, 'attachSecurityGroups').and.callFake(function(app, derivedGroups, groupsByName) {
        app.securityGroups = derivedGroups.concat(groupsByName);
        return $q.when(app);
      });

      return applicationReader.getApplication('deck', options);
    }

    describe('loading executions', function () {
      it('loads executions and sets appropriate flags', function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([{status: 'COMPLETED', stages: []}]));
        var result = null;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.executionsLoaded).toBe(true);
        expect(result.executionsLoading).toBe(false);
        expect(result.executionsLoadFailure).toBe(false);
      });

      it('sets appropriate flags when execution load fails', function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.reject(null));
        var result = null;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.executionsLoaded).toBe(false);
        expect(result.executionsLoading).toBe(false);
        expect(result.executionsLoadFailure).toBe(true);
      });
    });

    describe('reload executions', function () {
      it('reloads executions and sets appropriate flags', function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.when([{status: 'COMPLETED', stages: []}]));
        var result    = null,
            nextCalls = 0;
        loadApplication([], [], [], {executions: true}).then((app) => {
          result = app;
          result.executionRefreshStream.subscribe(() => nextCalls++);
        });
        $scope.$digest();
        $http.flush();
        expect(result.executionsLoaded).toBe(true);
        expect(result.executionsLoading).toBe(false);
        expect(result.executionsLoadFailure).toBe(false);

        result.reloadExecutions();
        expect(result.executionsLoading).toBe(true);

        $scope.$digest();
        expect(result.executionsLoaded).toBe(true);
        expect(result.executionsLoading).toBe(false);
        expect(result.executionsLoadFailure).toBe(false);

        expect(nextCalls).toBe(1);
      });

      it('sets appropriate flags when executions reload fails; subscriber is responsible for error checking', function () {
        spyOn(executionService, 'getRunningExecutions').and.returnValue($q.reject(null));
        var result        = null,
            errorsHandled = 0;
        loadApplication([], [], []).then((app) => {
          result = app;
          result.executionRefreshStream.subscribe(() => {
            if (result.executionsLoadFailure) {
              errorsHandled++;
            }
          });
        });
        $scope.$digest();
        $http.flush();

        result.reloadExecutions();
        $scope.$digest();

        expect(result.executionsLoading).toBe(false);
        expect(result.executionsLoadFailure).toBe(true);

        result.reloadExecutions();
        $scope.$digest();

        expect(errorsHandled).toBe(2);
      });
    });

    describe('loading tasks', function () {
      it('loads tasks and sets appropriate flags', function () {
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([{status: 'COMPLETED'}]));
        var result = null;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.tasksLoaded).toBe(true);
        expect(result.tasksLoading).toBe(false);
        expect(result.tasksLoadFailure).toBe(false);
      });

      it('sets appropriate flags when task load fails', function () {
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.reject(null));
        var result = null;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();
        expect(result.tasksLoaded).toBe(false);
        expect(result.tasksLoading).toBe(false);
        expect(result.tasksLoadFailure).toBe(true);
      });
    });

    describe('reload tasks', function () {
      it('reloads tasks and sets appropriate flags', function () {
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.when([{status: 'COMPLETED'}]));
        var result = null,
            nextCalls = 0;
        loadApplication([], [], [], {tasks: true}).then((app) => {
          result = app;
          result.taskRefreshStream.subscribe(() => nextCalls++);
        });
        $scope.$digest();
        $http.flush();
        expect(result.tasksLoaded).toBe(true);
        expect(result.tasksLoading).toBe(false);
        expect(result.tasksLoadFailure).toBe(false);

        result.reloadTasks();
        expect(result.tasksLoading).toBe(true);

        $scope.$digest();
        expect(result.tasksLoaded).toBe(true);
        expect(result.tasksLoading).toBe(false);
        expect(result.tasksLoadFailure).toBe(false);

        expect(nextCalls).toBe(1);
      });

      it('sets appropriate flags when task reload fails; subscriber is responsible for error checking', function () {
        spyOn(taskReader, 'getRunningTasks').and.returnValue($q.reject(null));
        var result = null,
            errorsHandled = 0;
        loadApplication([], [], []).then((app) => {
          result = app;
          result.taskRefreshStream.subscribe(() => {
            if (result.tasksLoadFailure) {
              errorsHandled++;
            }
          });
        });
        $scope.$digest();
        $http.flush();

        result.reloadTasks();
        $scope.$digest();

        expect(result.tasksLoading).toBe(false);
        expect(result.tasksLoadFailure).toBe(true);

        result.reloadTasks();
        $scope.$digest();

        expect(errorsHandled).toBe(2);
      });
    });

    describe('loading pipeline configs', function () {
      it('loads configs and sets appropriate flags', function () {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.when([]));
        var result = null;
        loadApplication([], [], []).then((app) => {
          result = app;
        });
        $scope.$digest();
        $http.flush();

        result.reloadPipelineConfigs();
        expect(result.pipelineConfigsLoading).toBe(true);
        $scope.$digest();

        expect(result.pipelineConfigsLoaded).toBe(true);
        expect(result.pipelineConfigsLoading).toBe(false);
        expect(result.pipelineConfigsLoadFailure).toBe(false);
      });

      it('sets appropriate flags when pipeline config reload fails; subscriber is responsible for error checking', function () {
        spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([]));
        spyOn(pipelineConfigService, 'getStrategiesForApplication').and.returnValue($q.reject([]));
        var result = null,
            errorsHandled = 0;
        loadApplication([], [], []).then((app) => {
          result = app;
          result.pipelineConfigRefreshStream.subscribe(() => {
            if (result.pipelineConfigsLoadFailure) {
              errorsHandled++;
            }
          });
        });
        $scope.$digest();
        $http.flush();

        result.reloadPipelineConfigs();
        $scope.$digest();

        expect(result.pipelineConfigsLoading).toBe(false);
        expect(result.pipelineConfigsLoadFailure).toBe(true);

        result.reloadPipelineConfigs();
        $scope.$digest();

        expect(errorsHandled).toBe(2);
      });
    });

    describe('setting default credentials and regions', function () {
      it('sets default credentials and region from server group when only one account/region found', function () {
        var serverGroups = [{
              name: 'deck-test-v001',
              cluster: 'deck-test',
              account: 'test',
              region: 'us-west-2',
              provider: 'aws',
              instances: []
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
                instances: []
              },
              {
                name: 'deck-gce-v001',
                account: 'gce-test',
                region: 'us-central-1',
                provider: 'gce',
                instances: [],
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

  describe('adding executions to applications', function () {
    it('should add all executions if there are none on application', function () {
      let execs = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toBe(execs);
    });

    it('should add new executions', function () {
      let original = {id:1, stringVal: 'ac'};
      let newOne = {id:2, stringVal: 'ab'};
      let execs = [original, newOne];
      application.executions = [original];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([original, newOne]);
    });

    it('should replace an existing execution if stringVal has changed', function () {
      let original = {id:1, stringVal: 'ac'};
      let updated = {id:1, stringVal: 'ab'};
      let execs = [updated];
      application.executions = [original];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([updated]);
    });

    it('should remove an execution if it is not in the new set', function () {
      let transient = {id:1, stringVal: 'ac'};
      let persistent = {id:2, stringVal: 'ab'};
      let execs = [persistent];
      application.executions = [transient];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([persistent]);
    });

    it('should remove multiple executions if not in the new set', function () {
      let transient1 = {id:1, stringVal: 'ac'};
      let persistent = {id:2, stringVal: 'ab'};
      let transient3 = {id:3, stringVal: 'ac'};
      let execs = [persistent];
      application.executions = [transient1, persistent, transient3];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([persistent]);
    });

    it('should replace the existing executions if application has executions comes back empty', function () {
      let execs = [];
      application.executions = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([]);
    });

  });
});


