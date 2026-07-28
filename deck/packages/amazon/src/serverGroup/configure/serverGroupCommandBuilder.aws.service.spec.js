'use strict';

import { AccountService, SubnetReader } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import {
  createCustomMockLaunchTemplate,
  mockLaunchTemplate,
  mockLaunchTemplateData,
  mockServerGroup,
} from '@spinnaker/mocks';
import { createAwsServerGroupCommandBuilder } from './serverGroupCommandBuilder.service';

describe('Service: awsServerGroup', function () {
  let instanceTypeService;
  beforeEach(function () {
    instanceTypeService = {
      getCategoryForMultipleInstanceTypes: jasmine
        .createSpy('getCategoryForMultipleInstanceTypes')
        .and.returnValue(Promise.resolve('custom')),
    };
    this.service = createAwsServerGroupCommandBuilder(instanceTypeService);
  });

  afterEach(AWSProviderSettings.resetToOriginal);

  describe('buildServerGroupCommandFromPipeline', function () {
    beforeEach(function () {
      this.cluster = {
        loadBalancers: ['elb-1'],
        account: 'prod',
        availabilityZones: {
          'us-west-1': ['d', 'g'],
        },
        capacity: {
          min: 1,
          max: 1,
        },
        instanceType: 'm5.large',
      };

      AWSProviderSettings.defaults = {
        account: 'test',
        region: 'us-east-1',
      };

      spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(Promise.resolve(['d', 'g']));

      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
        Promise.resolve({
          test: ['us-east-1', 'us-west-1'],
          prod: ['us-west-1', 'eu-west-1'],
        }),
      );
    });

    it('applies account, region from cluster', async function () {
      const command = await this.service.buildServerGroupCommandFromPipeline({}, this.cluster);

      expect(command.credentials).toBe('prod');
      expect(command.region).toBe('us-west-1');
    });

    it('sets usePreferredZones', async function () {
      let command = await this.service.buildServerGroupCommandFromPipeline({}, this.cluster);
      expect(command.viewState.usePreferredZones).toBe(true);

      // remove an availability zone, should be false
      this.cluster.availabilityZones['us-west-1'].pop();
      command = await this.service.buildServerGroupCommandFromPipeline({}, this.cluster);
      expect(command.viewState.usePreferredZones).toBe(false);
    });

    describe('extracts instanceProfile from pipeline cluster correctly', function () {
      const cluster = {
        account: 'prod',
        availabilityZones: {
          'us-west-1': ['d', 'g'],
        },
        capacity: {
          min: 1,
          max: 1,
        },
        instanceType: 'm5.large',
      };

      const clusters = [
        {
          desc: 'single instance type',
          cluster: {
            ...cluster,
            instanceType: 'r5.large',
          },
          expected: {
            instanceTypes: ['r5.large'],
            useSimpleInstanceTypeSelector: true,
          },
        },
        {
          desc: 'multiple instance types',
          cluster: {
            ...cluster,
            spotAllocationStrategy: 'capacity-optimized',
            launchTemplateOverridesForInstanceType: [
              {
                instanceType: 't3.nano',
                weightedCapacity: '2',
              },
              {
                instanceType: 't3.micro',
                weightedCapacity: '4',
              },
            ],
          },
          expected: {
            instanceTypes: ['t3.nano', 't3.micro'],
            useSimpleInstanceTypeSelector: false,
          },
        },
      ];

      clusters.forEach((test) => {
        it(`cluster with ${test.desc}`, async function () {
          const actualCommand = await this.service.buildServerGroupCommandFromPipeline({}, test.cluster);

          expect(instanceTypeService.getCategoryForMultipleInstanceTypes).toHaveBeenCalledWith(
            'aws',
            test.expected.instanceTypes,
          );
          expect(actualCommand.viewState.useSimpleInstanceTypeSelector).toBe(
            test.expected.useSimpleInstanceTypeSelector,
          );
        });
      });
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    beforeEach(function () {
      spyOn(AccountService, 'getPreferredZonesByAccount').and.returnValue(Promise.resolve([]));
      spyOn(SubnetReader, 'listSubnets').and.returnValue(Promise.resolve([]));
    });

    it('retains non-core suspended processes', async function () {
      var serverGroup = {
        asg: {
          availabilityZones: [],
          vpczoneIdentifier: '',
          suspendedProcesses: [
            { processName: 'Launch' },
            { processName: 'Terminate' },
            { processName: 'AZRebalance' },
            { processName: 'AddToLoadBalancer' },
          ],
        },
        launchTemplate: mockLaunchTemplate,
      };
      const command = await this.service.buildServerGroupCommandFromExisting({}, serverGroup);
      expect(command.suspendedProcesses).toEqual(['AZRebalance']);
    });

    it('sets source capacity flags when creating for pipeline', async function () {
      var serverGroup = {
        asg: {
          availabilityZones: [],
          vpczoneIdentifier: '',
          suspendedProcesses: [],
        },
        launchTemplate: mockLaunchTemplate,
      };
      const command = await this.service.buildServerGroupCommandFromExisting({}, serverGroup, 'editPipeline');

      expect(command.viewState.useSimpleCapacity).toBe(false);
      expect(command.useSourceCapacity).toBe(true);
    });

    describe('extracts properties from server group correctly', function () {
      const sgCommon = {
        ...mockServerGroup,
        asg: {
          autoScalingGroupName: 'myasg-test-v000',
          availabilityZones: [],
          vpczoneIdentifier: '',
          suspendedProcesses: [],
          enabledMetrics: [],
        },
      };

      const testLt = createCustomMockLaunchTemplate('test-lt', {
        ...mockLaunchTemplateData,
        instanceMarketOptions: {
          spotOptions: {
            maxPrice: '0.50',
          },
        },
        networkInterfaces: [
          {
            deviceIndex: 0,
            groups: ['my-sg'],
            ipv6AddressCount: 1,
            ipv6Addresses: [],
            privateIpAddresses: [],
          },
        ],
        keyName: 'test',
        kernelId: 'kernal-abc',
        ramDiskId: 'ramDisk-123',
        userData: 'thisisfakeuserdata',
        instanceType: 'm5.large',
      });

      const serverGroupsWithoutMip = [
        {
          desc: 'launchConfig',
          sg: {
            ...sgCommon,
            launchConfig: {
              instanceType: 'r5.large',
              securityGroups: [],
            },
          },
          expected: {
            instanceType: 'r5.large',
            launchTemplateOverridesForInstanceType: undefined,
            instanceTypesParam: ['r5.large'],
            useSimpleInstanceTypeSelector: true,
          },
        },
        {
          desc: 'launchTemplate',
          sg: {
            ...sgCommon,
            launchTemplate: testLt,
          },
          expected: {
            instanceType: 'm5.large',
            launchTemplateOverridesForInstanceType: undefined,
            instanceTypesParam: ['m5.large'],
            useSimpleInstanceTypeSelector: true,
            spotPrice: '0.50',
            associateIPv6Address: true,
            securityGroups: ['my-sg'],
          },
        },
      ];

      const mipCommon = {
        instancesDistribution: {
          onDemandAllocationStrategy: 'prioritized',
          onDemandBaseCapacity: 1,
          onDemandPercentageAboveBaseCapacity: 50,
          spotAllocationStrategy: 'capacity-optimized',
        },
      };

      const serverGroupsWithMip = [
        {
          desc: 'mixedInstancesPolicy without overrides',
          sg: {
            ...sgCommon,
            mixedInstancesPolicy: {
              instancesDistribution: {
                onDemandAllocationStrategy: 'prioritized',
                onDemandBaseCapacity: 1,
                onDemandPercentageAboveBaseCapacity: 50,
                spotAllocationStrategy: 'capacity-optimized',
                spotMaxPrice: '1.5',
              },
              allowedInstanceTypes: ['m5.large'],
              launchTemplates: [testLt],
            },
          },
          expected: {
            instanceType: 'm5.large',
            launchTemplateOverridesForInstanceType: undefined,
            instanceTypesParam: ['m5.large'],
            useSimpleInstanceTypeSelector: false,
            spotPrice: '1.5',
            associateIPv6Address: true,
            securityGroups: ['my-sg'],
          },
        },
        {
          desc: 'mixedInstancesPolicy with overrides and no priority',
          sg: {
            ...sgCommon,
            mixedInstancesPolicy: {
              ...mipCommon,
              launchTemplates: [mockLaunchTemplate],
              allowedInstanceTypes: ['t3.nano', 'm5.large'],
              launchTemplateOverridesForInstanceType: [
                { instanceType: 't3.nano', weightedCapacity: '2' },
                { instanceType: 'm5.large', weightedCapacity: '4' },
              ],
            },
          },
          expected: {
            instanceType: undefined,
            launchTemplateOverridesForInstanceType: [
              { instanceType: 't3.nano', priority: 1, weightedCapacity: '2' },
              { instanceType: 'm5.large', priority: 2, weightedCapacity: '4' },
            ],
            instanceTypesParam: ['t3.nano', 'm5.large'],
            useSimpleInstanceTypeSelector: false,
          },
        },
        {
          desc: 'mixedInstancesPolicy with overrides and all instance types have priority',
          sg: {
            ...sgCommon,
            mixedInstancesPolicy: {
              ...mipCommon,
              launchTemplates: [mockLaunchTemplate],
              allowedInstanceTypes: ['t3.nano', 'm5.large'],
              launchTemplateOverridesForInstanceType: [
                { instanceType: 't3.nano', weightedCapacity: '2', priority: 2 },
                { instanceType: 'm5.large', weightedCapacity: '4', priority: 1 },
              ],
            },
          },
          expected: {
            instanceType: undefined,
            launchTemplateOverridesForInstanceType: [
              ,
              { instanceType: 'm5.large', weightedCapacity: '4', priority: 1 },
              { instanceType: 't3.nano', weightedCapacity: '2', priority: 2 },
            ],
            instanceTypesParam: ['t3.nano', 'm5.large'],
            useSimpleInstanceTypeSelector: false,
          },
        },
        {
          desc: 'mixedInstancesPolicy with overrides and some instance types have priority',
          sg: {
            ...sgCommon,
            mixedInstancesPolicy: {
              ...mipCommon,
              launchTemplates: [mockLaunchTemplate],
              allowedInstanceTypes: ['t3.nano', 't3.micro', 't2.nano', 't2.micro'],
              launchTemplateOverridesForInstanceType: [
                { instanceType: 't3.nano' },
                { instanceType: 't3.micro', priority: 2 },
                { instanceType: 't2.nano' },
                { instanceType: 't2.micro', priority: 1 },
              ],
            },
          },
          expected: {
            instanceType: undefined,
            launchTemplateOverridesForInstanceType: [
              { instanceType: 't2.micro', priority: 1 },
              { instanceType: 't3.micro', priority: 2 },
              { instanceType: 't3.nano', priority: 3 },
              { instanceType: 't2.nano', priority: 4 },
            ],
            instanceTypesParam: ['t3.nano', 't3.micro', 't2.nano', 't2.micro'],
            useSimpleInstanceTypeSelector: false,
          },
        },
      ];

      [...serverGroupsWithoutMip, ...serverGroupsWithMip].forEach((test) => {
        it(`extracts instanceProfile, instanceType and useSimpleInstanceTypeSelector from server group with ${test.desc} correctly`, async function () {
          const actualCommand = await this.service.buildServerGroupCommandFromExisting({}, test.sg, 'clone');

          expect(instanceTypeService.getCategoryForMultipleInstanceTypes).toHaveBeenCalledWith(
            'aws',
            test.expected.instanceTypesParam,
          );
          expect(actualCommand.instanceType).toBe(test.expected.instanceType);
          expect(actualCommand.viewState.useSimpleInstanceTypeSelector).toBe(
            test.expected.useSimpleInstanceTypeSelector,
          );
          test.expected.spotPrice && expect(actualCommand.spotPrice).toBe(test.expected.spotPrice);
        });
      });

      serverGroupsWithMip.forEach((test) => {
        it(`extracts launchTemplateOverridesForInstanceType and sets explicit priority correctly for server group with ${test.desc}`, async function () {
          const actualCommand = await this.service.buildServerGroupCommandFromExisting({}, test.sg, 'clone');

          expect(
            _.isEqual(
              actualCommand.launchTemplateOverridesForInstanceType,
              test.expected.launchTemplateOverridesForInstanceType,
            ).toBeTruthy,
          );
        });
      });
    });
  });
});
