'use strict';

import { CloudProviderRegistry } from '../../../../cloudProvider';

describe('DeployExecutionDetailsCtrl', function () {
  beforeEach(window.module(require('./deployExecutionDetails.controller').name));

  let originalGetValue;
  beforeAll(() => {
    originalGetValue = CloudProviderRegistry.getValue;
    CloudProviderRegistry.getValue = (cp) => cp === 'withScalingActivities';
  });
  afterAll(() => (CloudProviderRegistry.getValue = originalGetValue));

  beforeEach(
    window.inject(function ($controller, $rootScope, $timeout) {
      this.$controller = $controller;
      this.$timeout = $timeout;
      this.$scope = $rootScope.$new();
    }),
  );

  beforeEach(function () {
    this.$scope.stage = {};
    this.$scope.application = {};
    this.initializeController = function () {
      this.controller = this.$controller('DeployExecutionDetailsCtrl', {
        $scope: this.$scope,
        $stateParams: { details: 'deploymentConfig' },
        executionDetailsSectionService: { synchronizeSection: (a, fn) => fn() },
      });
    };
  });

  describe('deployment results', function () {
    it('sets empty list when no context or empty context (except for changes config)', function () {
      var stage = this.$scope.stage;
      stage.context = {
        commits: [],
        jarDiffs: {},
      };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context = {};
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);
    });

    it('sets empty list when no kato.tasks or empty kato.tasks', function () {
      var stage = this.$scope.stage;

      stage.context = {};
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context = {
        'kato.tasks': [],
      };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'].push({});
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects = [];
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects.push({});
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects[0].serverGroupNameByRegion = {
        'us-west-1': 'deployedAsg',
      };
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(1);
      expect(this.$scope.deployed[0].serverGroup).toBe('deployedAsg');
    });

    it('sets empty list when no resultObjects or empty resultObjects', function () {
      var stage = this.$scope.stage;

      stage.context = { 'kato.tasks': [{}] };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects = [];
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);
    });

    it('sets empty list when no serverGroupNameByRegion', function () {
      var stage = this.$scope.stage;

      stage.context = {
        'kato.tasks': [
          {
            resultObjects: [{ someField: true }],
          },
        ],
      };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects[0].serverGroupNameByRegion = {
        'us-west-1': 'deployedAsg',
      };
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(1);
      expect(this.$scope.deployed[0].serverGroup).toBe('deployedAsg');
    });

    it('sets deployed when serverGroupNameByRegion supplies values', function () {
      var stage = this.$scope.stage;

      stage.context = {
        'kato.tasks': [
          {
            resultObjects: [
              {
                serverGroupNameByRegion: { 'us-west-1': 'deployedWest', 'us-east-1': 'deployedEast' },
              },
            ],
          },
        ],
      };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(2);
    });
  });

  describe('running warnings', function () {
    beforeEach(function () {
      this.$scope.stage = {
        isRunning: true,
        context: {
          cloudProvider: 'aws',
          'kato.tasks': [{ resultObjects: [{ serverGroupNameByRegion: { 'us-west-1': 'deployedWest' } }] }],
        },
        tasks: [
          { name: 'forceCacheRefresh', status: 'RUNNING' },
          { name: 'waitForUpInstances', status: 'NOT_STARTED' },
        ],
      };
    });

    it('sets waitingForUpInstances flag when waitForUpInstances is running and lastCapacityCheck reported', function () {
      this.initializeController();
      expect(this.$scope.waitingForUpInstances).toBe(false);

      this.$scope.stage.tasks[0].status = 'COMPLETED';
      this.$scope.stage.tasks[1].status = 'RUNNING';

      this.initializeController();
      expect(this.$scope.waitingForUpInstances).toBe(false);

      this.$scope.stage.context.lastCapacityCheck = {};

      this.initializeController();
      expect(this.$scope.waitingForUpInstances).toBe(true);
    });

    it('sets showScalingActivitiesLink if configured for cloud provider and three minutes have passed', function () {
      this.$scope.stage.context.lastCapacityCheck = {
        up: 1,
        down: 0,
        outOfService: 0,
        unknown: 0,
        succeeded: 0,
        failed: 0,
      };
      this.$scope.stage.context.capacity = { desired: 2 };
      this.$scope.stage.tasks[0].status = 'COMPLETED';
      this.$scope.stage.tasks[1].status = 'RUNNING';
      this.initializeController();
      expect(this.$scope.showScalingActivitiesLink).toBe(false);

      this.$scope.stage.context.cloudProvider = 'withScalingActivities';
      this.initializeController();
      expect(this.$scope.showScalingActivitiesLink).toBe(false);

      this.$scope.stage.tasks[1].runningTimeInMs = 5 * 60 * 1000 + 1;
      this.initializeController();
      expect(this.$scope.showScalingActivitiesLink).toBe(true);
    });

    it('sets showPlatformHealthOverrideMessage after three minutes if unknown status detected and platformHealthOverride not configured', function () {
      this.$scope.stage.context.lastCapacityCheck = {
        up: 0,
        down: 0,
        outOfService: 0,
        unknown: 1,
        succeeded: 0,
        failed: 0,
      };
      this.$scope.stage.context.capacity = { desired: 1 };
      this.$scope.stage.tasks[0].status = 'COMPLETED';
      this.$scope.stage.tasks[1].status = 'RUNNING';
      this.$scope.stage.tasks[1].runningTimeInMs = 5 * 60 * 1000 + 1;
      this.$scope.application.attributes = {};
      this.initializeController();
      expect(this.$scope.showPlatformHealthOverrideMessage).toBe(true);

      // do not show the message if platformHealthOverride is configured
      this.$scope.application.attributes.platformHealthOverride = true;
      this.initializeController();
      expect(this.$scope.showPlatformHealthOverrideMessage).toBe(false);

      // do not show the message if interestingHealthProviderNames are present
      this.$scope.application.attributes.platformHealthOverride = false;
      this.$scope.stage.context.interestingHealthProviderNames = ['Amazon'];
      this.initializeController();
      expect(this.$scope.showPlatformHealthOverrideMessage).toBe(false);
    });
  });
});
