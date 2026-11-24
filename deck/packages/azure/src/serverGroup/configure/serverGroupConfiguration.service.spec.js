'use strict';
import { nativePromiseService } from '@spinnaker/core';

import { AzureServerGroupConfigurationService } from './serverGroupConfiguration.service';

const testContext = {};

describe('Service: azureServerGroupConfiguration', function () {
  var service;

  beforeEach(function () {
    service = new AzureServerGroupConfigurationService(nativePromiseService, {
      cacheInitializer: {},
      loadBalancerReader: {},
      securityGroupReader: {},
    });

    testContext.allLoadBalancers = [
      {
        name: 'elb-1',
        accounts: [
          {
            name: 'test',
            regions: [
              {
                name: 'us-east-1',
                loadBalancers: [
                  { region: 'us-east-1', vpcId: null, name: 'elb-1' },
                  { region: 'us-east-1', vpcId: 'vpc-1', name: 'elb-1' },
                ],
              },
            ],
          },
        ],
      },
      {
        name: 'elb-2',
        accounts: [
          {
            name: 'test',
            regions: [
              {
                name: 'us-east-1',
                loadBalancers: [
                  { region: 'us-east-1', vpcId: null, name: 'elb-2' },
                  { region: 'us-east-1', vpcId: 'vpc-2', name: 'elb-2' },
                ],
              },
              {
                name: 'us-west-1',
                loadBalancers: [{ region: 'us-west-1', vpcId: null, name: 'elb-2' }],
              },
            ],
          },
        ],
      },
    ];
  });

  describe('configureSecurityGroupOptions', function () {
    beforeEach(function () {
      testContext.allSecurityGroups = {
        azurecred1: {
          eastus: [
            {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'eastus',
              },
            },
            {
              'onlyazure-cache': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-cache',
                name: 'onlyazure-cache',
                network: 'na',
                provider: 'azure',
                region: 'eastus',
              },
            },
          ],
          westus: [
            {
              'onlyazure-web': {
                account: 'azure-cred1',
                accountName: 'azure-cred1',
                id: 'onlyazure-web',
                name: 'onlyazure-web',
                network: 'na',
                provider: 'azure',
                region: 'westus',
              },
            },
          ],
        },
      };

      testContext.command = {
        backingData: {
          securityGroups: testContext.allSecurityGroups,
          filtered: {},
        },
        viewState: {
          securityGroupsConfigured: false,
        },
        credentials: 'azurecred1',
        region: 'westus',
      };
    });

    it('finds matching firewalls and assigns them to the filtered list the first time', function () {
      testContext.command.region = 'westus';
      var expected = testContext.allSecurityGroups.azurecred1['westus'];

      var result = service.configureSecurityGroupOptions(testContext.command);

      expect(testContext.command.backingData.filtered.securityGroups).toEqual(expected);
      expect(result).toEqual({ dirty: { securityGroups: true } });
      expect(testContext.command.viewState.securityGroupsConfigured).toBe(true);
    });

    it('finds matching firewalls, sets dirty flag for subsequent time', function () {
      testContext.command.region = 'eastus';
      testContext.command.backingData.filtered.securityGroups = testContext.allSecurityGroups.azurecred1['westus'];
      var expected = testContext.allSecurityGroups.azurecred1['eastus'];

      var result = service.configureSecurityGroupOptions(testContext.command);

      expect(testContext.command.backingData.filtered.securityGroups).toEqual(expected);
      expect(result).toEqual({ dirty: { securityGroups: true } });
      expect(testContext.command.viewState.securityGroupsConfigured).toBe(true);
    });

    it('clears the selected securityGroup', function () {
      testContext.command.selectedSecurityGroup = {
        'onlyazure-web': {
          account: 'azure-cred1',
          accountName: 'azure-cred1',
          id: 'onlyazure-web',
          name: 'onlyazure-web',
          network: 'na',
          provider: 'azure',
          region: 'westus',
        },
      };
      testContext.command.region = 'eastus';

      var result = service.configureSecurityGroupOptions(testContext.command);

      expect(testContext.command.selectedSecurityGroup).toBeNull();
      expect(result).toEqual({ dirty: { securityGroups: true } });
      expect(testContext.command.viewState.securityGroupsConfigured).toBe(true);
    });

    it('returns no firewalls if none match', function () {
      testContext.command.region = 'eastasia';
      testContext.command.backingData.filtered.securityGroups = testContext.allSecurityGroups.azurecred1['westus'];

      var result = service.configureSecurityGroupOptions(testContext.command);

      expect(testContext.command.selectedSecurityGroup).toBeUndefined();
      expect(result).toEqual({ dirty: { securityGroups: true } });
      expect(testContext.command.backingData.filtered.securityGroups).toEqual([]);
      expect(testContext.command.viewState.securityGroupsConfigured).toBe(false);
    });

    it('returns empty zone list if region is not supported', function () {
      testContext.command.region = 'eastasia';
      testContext.command.backingData.credentialsKeyedByAccount = {};
      testContext.command.backingData.credentialsKeyedByAccount[testContext.command.credentials] = {
        regionsSupportZones: [],
        availabilityZones: ['1', '2', '3'],
      };

      service.configureZones(testContext.command);

      expect(testContext.command.backingData.filtered.zones).toEqual([]);
    });

    it('returns actual zone list if region is supported', function () {
      testContext.command.region = 'eastasia';
      testContext.command.backingData.credentialsKeyedByAccount = {};
      testContext.command.backingData.credentialsKeyedByAccount[testContext.command.credentials] = {
        regionsSupportZones: ['eastasia'],
        availabilityZones: ['1', '2', '3'],
      };

      service.configureZones(testContext.command);

      expect(testContext.command.backingData.filtered.zones).toEqual(
        testContext.command.backingData.credentialsKeyedByAccount[testContext.command.credentials].availabilityZones,
      );
    });

    it('does not return zone list if region is not specified', function () {
      testContext.command.region = null;
      testContext.command.backingData.credentialsKeyedByAccount = {};
      testContext.command.backingData.credentialsKeyedByAccount[testContext.command.credentials] = {
        regionsSupportZones: ['eastasia'],
        availabilityZones: ['1', '2', '3'],
      };

      service.configureZones(testContext.command);

      expect(testContext.command.backingData.filtered.zones).toBeUndefined();
    });
  });
});
