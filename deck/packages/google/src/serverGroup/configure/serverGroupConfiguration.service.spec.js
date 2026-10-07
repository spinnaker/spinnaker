'use strict';

import { nativePromiseService } from '@spinnaker/core';

import {
  GCE_DISTRIBUTION_POLICY_TARGET_SHAPES,
  GceServerGroupConfigurationService,
} from './serverGroupConfiguration.service';

describe('GceServerGroupConfigurationService', () => {
  let service;

  beforeEach(() => {
    service = new GceServerGroupConfigurationService(nativePromiseService, {
      securityGroupReader: {},
      loadBalancerReader: {},
    });
  });

  describe('configureLoadBalancerOptions', () => {
    it('scopes EXTERNAL_MANAGED listener normalization by account and region without seeding global backends', () => {
      const command = {
        credentials: 'account-a',
        region: 'europe-west1',
        loadBalancers: ['external-listener'],
        backendServiceMetadata: ['europe-backend'],
        backingData: {
          loadBalancers: [
            {
              accounts: [
                {
                  name: 'account-a',
                  regions: [
                    {
                      loadBalancers: [
                        {
                          account: 'account-a',
                          listeners: [{ name: 'external-listener' }],
                          loadBalancerType: 'EXTERNAL_MANAGED',
                          name: 'external-listener',
                          provider: 'gce',
                          region: 'us-central1',
                          urlMapName: 'app-main',
                          backendServices: ['central-backend', 'other-backend'],
                        },
                        {
                          account: 'account-a',
                          listeners: [{ name: 'external-listener' }],
                          loadBalancerType: 'EXTERNAL_MANAGED',
                          name: 'external-listener',
                          provider: 'gce',
                          region: 'europe-west1',
                          urlMapName: 'app-main',
                          backendServices: ['europe-backend'],
                        },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
          filtered: {},
        },
      };

      service.configureLoadBalancerOptions(command);

      expect(command.loadBalancers).toEqual(['app-main (account-a/europe-west1/EXTERNAL_MANAGED)']);
      expect(command.backendServices).toBeUndefined();
    });
  });

  describe('target shapes', () => {
    it('exposes BALANCED and ANY_SINGLE_ZONE alongside ANY and EVEN', () => {
      expect(GCE_DISTRIBUTION_POLICY_TARGET_SHAPES).toEqual(['ANY', 'EVEN', 'BALANCED', 'ANY_SINGLE_ZONE']);
    });
  });
});
