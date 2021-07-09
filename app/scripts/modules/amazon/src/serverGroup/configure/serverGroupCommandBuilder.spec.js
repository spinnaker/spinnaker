'use strict';

import { AccountService, SubnetReader } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';

import { createMockAmazonServerGroupWithLt, createCustomMockLaunchTemplate } from '@spinnaker/mocks';

describe('awsServerGroupCommandBuilder', function () {
  const AccountServiceFixture = require('./AccountServiceFixtures');

  beforeEach(window.module(require('./serverGroupCommandBuilder.service').name));

  beforeEach(
    window.inject(function (awsServerGroupCommandBuilder, $q, $rootScope, instanceTypeService) {
      this.awsServerGroupCommandBuilder = awsServerGroupCommandBuilder;
      this.$scope = $rootScope;
      this.instanceTypeService = instanceTypeService;
      this.$q = $q;
      spyOn(AccountService, 'getPreferredZonesByAccount').and.returnValue(
        $q.when(AccountServiceFixture.preferredZonesByAccount),
      );
      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
        $q.when(AccountServiceFixture.credentialsKeyedByAccount),
      );
      spyOn(SubnetReader, 'listSubnets').and.returnValue($q.when([]));
      spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(this.$q.when(['a', 'b', 'c']));
    }),
  );

  afterEach(AWSProviderSettings.resetToOriginal);

  describe('buildNewServerGroupCommand', function () {
    it('initializes to default values, setting usePreferredZone flag to true', function () {
      let command = null;
      AWSProviderSettings.defaults.iamRole = '{{application}}IAMRole';
      this.awsServerGroupCommandBuilder
        .buildNewServerGroupCommand({ name: 'appo', defaultCredentials: {}, defaultRegions: {} }, 'aws')
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['a', 'b', 'c']);
      expect(command.iamRole).toBe('appoIAMRole');
    });

    it('sets unlimitedCpuCredits to undefined if not modified by user', function () {
      let command = null;
      AWSProviderSettings.defaults.iamRole = '{{application}}IAMRole';
      this.awsServerGroupCommandBuilder
        .buildNewServerGroupCommand({ name: 'test-app', defaultCredentials: {}, defaultRegions: {} }, 'aws')
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.unlimitedCpuCredits).toBe(undefined);
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('sets usePreferredZones flag based on initial value', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('custom'));
      const baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
        },
      };
      let command = null;

      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'appo' }, baseServerGroup)
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['g', 'h', 'i']);

      baseServerGroup.asg.availabilityZones = ['g'];

      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'appo' }, baseServerGroup)
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.viewState.usePreferredZones).toBe(false);
      expect(command.availabilityZones).toEqual(['g']);
    });

    it('sets profile and instance type if available', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      const baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
        },
        launchConfig: {
          instanceType: 'something-custom',
          instanceMonitoring: {},
          securityGroups: [],
        },
      };
      let command = null;

      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'appo' }, baseServerGroup)
        .then(function (result) {
          command = result;
        });

      this.$scope.$digest();

      expect(command.viewState.instanceProfile).toBe('selectedProfile');
      expect(command.instanceType).toBe('something-custom');
    });

    it('copies suspended processes unless the mode is "editPipeline"', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      const baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
          suspendedProcesses: [{ processName: 'x' }, { processName: 'a' }],
        },
        launchConfig: {
          instanceType: 'something-custom',
          instanceMonitoring: {},
          securityGroups: [],
        },
      };
      let command = null;

      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'appo' }, baseServerGroup)
        .then((result) => (command = result));

      this.$scope.$digest();

      expect(command.suspendedProcesses).toEqual(['x', 'a']);

      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'appo' }, baseServerGroup, 'editPipeline')
        .then((result) => (command = result));

      this.$scope.$digest();

      expect(command.suspendedProcesses).toEqual([]);
    });

    it('copies tags not in the reserved list:', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      const baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        tags: null,
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
          tags: [
            {
              key: 'some-key',
              propagateAtLaunch: true,
              resourceId: 'some-resource-id',
              resourceType: 'auto-scaling-group',
              value: 'some-value',
            },
            {
              key: 'spinnaker:application',
              value: 'n/a',
            },
          ],
        },
        launchConfig: {
          instanceType: 'something-custom',
          instanceMonitoring: {},
          securityGroups: [],
        },
      };
      let command = null;

      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'appo' }, baseServerGroup)
        .then((result) => (command = result));

      this.$scope.$digest();

      expect(command.tags).toEqual({ 'some-key': 'some-value' });
    });

    it('sets unlimitedCpuCredits to false when building from source server group with standard credits', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      const baseServerGroup = createMockAmazonServerGroupWithLt(
        createCustomMockLaunchTemplate('testLtCpuCredits', {
          creditSpecification: {
            cpuCredits: 'standard',
          },
        }),
      );

      let command = null;
      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'test' }, baseServerGroup)
        .then((result) => (command = result));

      this.$scope.$digest();

      expect(command.unlimitedCpuCredits).toBe(false);
    });

    it('sets unlimitedCpuCredits to true when building from source server group with unlimited credits', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      const baseServerGroup = createMockAmazonServerGroupWithLt(
        createCustomMockLaunchTemplate('testLtCpuCredits', {
          creditSpecification: {
            cpuCredits: 'unlimited',
          },
        }),
      );

      let command = null;
      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'test' }, baseServerGroup)
        .then((result) => (command = result));

      this.$scope.$digest();

      expect(command.unlimitedCpuCredits).toBe(true);
    });

    it('sets unlimitedCpuCredits to undefined when building from source server group with cpu credits unset', function () {
      spyOn(this.instanceTypeService, 'getCategoryForInstanceType').and.returnValue(this.$q.when('selectedProfile'));

      const baseServerGroup = createMockAmazonServerGroupWithLt();

      let command = null;
      this.awsServerGroupCommandBuilder
        .buildServerGroupCommandFromExisting({ name: 'test' }, baseServerGroup)
        .then((result) => (command = result));

      this.$scope.$digest();

      expect(command.unlimitedCpuCredits).toBe(undefined);
    });
  });
});
