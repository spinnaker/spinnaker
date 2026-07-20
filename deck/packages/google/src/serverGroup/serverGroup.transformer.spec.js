'use strict';

import { GceServerGroupTransformer } from './serverGroup.transformer';

describe('gceServerGroupTransformer', () => {
  let transformer;

  describe('normalize server group load balancers', () => {
    let app;
    beforeEach(() => {
      transformer = new GceServerGroupTransformer();
      app = {
        getDataSource: () => {
          return {
            ready: () => Promise.resolve(),
            data: [
              { name: 'network-load-balancer', account: 'my-google-account' },
              { name: 'internal-load-balancer', account: 'my-google-account' },
              {
                name: 'url-map-name',
                provider: 'gce',
                account: 'my-google-account',
                region: 'global',
                loadBalancerType: 'HTTP',
                listeners: [{ name: 'http-load-balancer-listener' }, { name: 'https-load-balancer-listener' }],
              },
            ],
          };
        },
      };
    });

    it('should map listener names to url map names', async function () {
      const serverGroup = {
        account: 'my-google-account',
        loadBalancers: [
          'network-load-balancer',
          'internal-load-balancer',
          'http-load-balancer-listener',
          'https-load-balancer-listener',
        ],
      };

      const normalizedServerGroup = await transformer.normalizeServerGroup(serverGroup, app);
      expect(normalizedServerGroup.loadBalancers.length).toBe(3);
      expect(normalizedServerGroup.loadBalancers.includes('url-map-name')).toEqual(true);
      expect(normalizedServerGroup.loadBalancers.includes('network-load-balancer')).toEqual(true);
      expect(normalizedServerGroup.loadBalancers.includes('internal-load-balancer')).toEqual(true);
    });
  });

  describe('convert server group command to deploy configuration', () => {
    beforeEach(() => {
      transformer = new GceServerGroupTransformer();
    });

    it('uses existing availability zones when pipeline commands do not have backing data', () => {
      const deployConfig = transformer.convertServerGroupCommandToDeployConfiguration({
        availabilityZones: { 'us-central1': ['us-central1-a', 'us-central1-b'] },
        capacity: { desired: 2, max: 2, min: 2 },
        credentials: 'my-google-account',
        enableTraffic: true,
        instanceMetadata: {},
        region: 'us-central1',
        viewState: { mode: 'editPipeline' },
      });

      expect(deployConfig.availabilityZones).toEqual({ 'us-central1': ['us-central1-a', 'us-central1-b'] });
      expect(deployConfig.account).toBe('my-google-account');
      expect(deployConfig.backingData).toBeUndefined();
    });

    it('preserves instanceFlexibilityPolicy for pipeline edit/save round-trips', () => {
      const flexibilityPolicy = {
        instanceSelections: {
          preferred: { machineTypes: ['n2-standard-8'] },
          fallback: { rank: 2, machineTypes: ['e2-standard-8'] },
        },
      };
      const command = {
        credentials: 'test-account',
        region: 'us-central1',
        zone: 'us-central1-a',
        enableTraffic: true,
        instanceFlexibilityPolicy: flexibilityPolicy,
        backingData: { filtered: { truncatedZones: ['us-central1-a'] } },
        viewState: { mode: 'editPipeline' },
      };

      const deployConfig = transformer.convertServerGroupCommandToDeployConfiguration(command);

      expect(deployConfig.instanceFlexibilityPolicy).toEqual(flexibilityPolicy);
      expect(deployConfig.backingData).toBeUndefined();
      expect(deployConfig.viewState).toBeUndefined();
    });
  });
});
