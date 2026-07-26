'use strict';

import { AccountService, SubnetReader } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import { createAwsServerGroupCommandBuilder } from './serverGroupCommandBuilder.service';

import {
  createMockAmazonServerGroupWithLt,
  createCustomMockLaunchTemplate,
  mockLaunchTemplate,
} from '@spinnaker/mocks';

describe('awsServerGroupCommandBuilder', function () {
  const AccountServiceFixture = require('./AccountServiceFixtures');

  beforeEach(function () {
    this.instanceTypeService = {
      getCategoryForMultipleInstanceTypes: jasmine.createSpy('getCategoryForMultipleInstanceTypes'),
    };
    this.awsServerGroupCommandBuilder = createAwsServerGroupCommandBuilder(this.instanceTypeService);
    spyOn(AccountService, 'getPreferredZonesByAccount').and.returnValue(
      Promise.resolve(AccountServiceFixture.preferredZonesByAccount),
    );
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve(AccountServiceFixture.credentialsKeyedByAccount),
    );
    spyOn(SubnetReader, 'listSubnets').and.returnValue(Promise.resolve([]));
    spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(Promise.resolve(['a', 'b', 'c']));
  });

  afterEach(AWSProviderSettings.resetToOriginal);

  describe('buildNewServerGroupCommand', function () {
    it('initializes to default values, setting usePreferredZone flag to true', async function () {
      AWSProviderSettings.defaults.iamRole = '{{application}}IAMRole';
      const command = await this.awsServerGroupCommandBuilder.buildNewServerGroupCommand(
        { name: 'appo', defaultCredentials: {}, defaultRegions: {} },
        'aws',
      );

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['a', 'b', 'c']);
      expect(command.iamRole).toBe('appoIAMRole');
    });

    it('sets unlimitedCpuCredits to undefined if not modified by user', async function () {
      AWSProviderSettings.defaults.iamRole = '{{application}}IAMRole';
      const command = await this.awsServerGroupCommandBuilder.buildNewServerGroupCommand(
        { name: 'test-app', defaultCredentials: {}, defaultRegions: {} },
        'aws',
      );

      expect(command.unlimitedCpuCredits).toBe(undefined);
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('sets usePreferredZones flag based on initial value', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('custom'));
      const baseServerGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g', 'h', 'i'],
          vpczoneIdentifier: '',
        },
        launchTemplate: mockLaunchTemplate,
      };
      let command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'appo' },
        baseServerGroup,
      );

      expect(command.viewState.usePreferredZones).toBe(true);
      expect(command.availabilityZones).toEqual(['g', 'h', 'i']);

      baseServerGroup.asg.availabilityZones = ['g'];

      command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'appo' },
        baseServerGroup,
      );

      expect(command.viewState.usePreferredZones).toBe(false);
      expect(command.availabilityZones).toEqual(['g']);
    });

    it('sets profile and instance type if available', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('selectedProfile'));

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
      const command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'appo' },
        baseServerGroup,
      );

      expect(command.viewState.instanceProfile).toBe('selectedProfile');
      expect(command.instanceType).toBe('something-custom');
    });

    it('copies suspended processes unless the mode is "editPipeline"', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('selectedProfile'));

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
      let command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'appo' },
        baseServerGroup,
      );

      expect(command.suspendedProcesses).toEqual(['x', 'a']);

      command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'appo' },
        baseServerGroup,
        'editPipeline',
      );

      expect(command.suspendedProcesses).toEqual([]);
    });

    it('copies tags not in the reserved list:', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('selectedProfile'));

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
      const command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'appo' },
        baseServerGroup,
      );

      expect(command.tags).toEqual({ 'some-key': 'some-value' });
    });

    it('sets unlimitedCpuCredits to false when building from source server group with standard credits', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('selectedProfile'));

      const baseServerGroup = createMockAmazonServerGroupWithLt(
        createCustomMockLaunchTemplate('testLtCpuCredits', {
          creditSpecification: {
            cpuCredits: 'standard',
          },
        }),
      );

      const command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'test' },
        baseServerGroup,
      );

      expect(command.unlimitedCpuCredits).toBe(false);
    });

    it('sets unlimitedCpuCredits to true when building from source server group with unlimited credits', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('selectedProfile'));

      const baseServerGroup = createMockAmazonServerGroupWithLt(
        createCustomMockLaunchTemplate('testLtCpuCredits', {
          creditSpecification: {
            cpuCredits: 'unlimited',
          },
        }),
      );

      const command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'test' },
        baseServerGroup,
      );

      expect(command.unlimitedCpuCredits).toBe(true);
    });

    it('sets unlimitedCpuCredits to undefined when building from source server group with cpu credits unset', async function () {
      this.instanceTypeService.getCategoryForMultipleInstanceTypes.and.returnValue(Promise.resolve('selectedProfile'));

      const baseServerGroup = createMockAmazonServerGroupWithLt();

      const command = await this.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'test' },
        baseServerGroup,
      );

      expect(command.unlimitedCpuCredits).toBe(undefined);
    });
  });
});
