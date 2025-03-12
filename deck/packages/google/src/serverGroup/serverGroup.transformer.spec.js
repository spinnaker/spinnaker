'use strict';

describe('gceServerGroupTransformer', () => {
  let transformer, $q, $scope;
  beforeEach(window.module(require('./serverGroup.transformer').name));

  beforeEach(() => {
    window.inject((_$q_, $rootScope, _gceServerGroupTransformer_) => {
      $q = _$q_;
      $scope = $rootScope.$new();
      transformer = _gceServerGroupTransformer_;
    });
  });

  describe('normalize server group load balancers', () => {
    let app;
    beforeEach(() => {
      app = {
        getDataSource: () => {
          return {
            ready: () => $q.resolve(),
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

    it('should map listener names to url map names', function () {
      const serverGroup = {
        account: 'my-google-account',
        loadBalancers: [
          'network-load-balancer',
          'internal-load-balancer',
          'http-load-balancer-listener',
          'https-load-balancer-listener',
        ],
      };

      let normalizedServerGroup;
      transformer.normalizeServerGroup(serverGroup, app).then((normalized) => (normalizedServerGroup = normalized));
      $scope.$digest();
      expect(normalizedServerGroup.loadBalancers.length).toBe(3);
      expect(normalizedServerGroup.loadBalancers.includes('url-map-name')).toEqual(true);
      expect(normalizedServerGroup.loadBalancers.includes('network-load-balancer')).toEqual(true);
      expect(normalizedServerGroup.loadBalancers.includes('internal-load-balancer')).toEqual(true);
    });
  });
});
