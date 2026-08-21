'use strict';

import { nativePromiseService } from '@spinnaker/core';

import { GceServerGroupConfigurationService } from './serverGroupConfiguration.service';

describe('GceServerGroupConfigurationService', () => {
  let service;

  beforeEach(() => {
    service = new GceServerGroupConfigurationService(nativePromiseService, {
      securityGroupReader: {},
      loadBalancerReader: {},
    });
  });

  describe('configureLoadBalancerOptions', () => {
    it('scopes EXTERNAL_MANAGED listener normalization and backend mapping by account and region', () => {
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
      expect(command.backendServices).toEqual({
        'app-main (account-a/europe-west1/EXTERNAL_MANAGED)': ['europe-backend'],
      });
    });
  });
});
