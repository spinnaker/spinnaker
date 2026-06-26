import { AccountService, ApplicationModelBuilder } from '@spinnaker/core';

describe('Controller: LoadBalancerDetailsCtrl', function () {
  //NOTE: This is just a skeleton test to test DI.  Please add more tests.;

  let controller;
  let $scope;
  let $state;
  const loadBalancer = {
    name: 'foo',
    region: 'us-west-1',
    account: 'test',
    accountId: 'test',
    vpcId: '1',
  };

  beforeEach(window.module(require('./loadBalancerDetail.controller').name));

  beforeEach(
    window.inject(function ($controller, $rootScope, _$q_, _$state_) {
      $scope = $rootScope.$new();
      $state = _$state_;
      spyOn(AccountService, 'getAccountDetails').and.returnValue(_$q_.resolve({ project: 'test-project' }));
      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(_$q_.resolve({}));
      const app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      app.loadBalancers.data.push(loadBalancer);
      controller = $controller('gceLoadBalancerDetailsCtrl', {
        $scope: $scope,
        loadBalancer: loadBalancer,
        app: app,
        $state: $state,
        loadBalancerReader: {
          getLoadBalancerDetails: () =>
            _$q_.resolve([
              {
                dnsname: '1.2.3.4',
                listenerDescriptions: [{ listener: { loadBalancerPort: '80' } }],
                vpcid: '1',
              },
            ]),
        },
      });
    }),
  );

  it('should have an instantiated controller', function () {
    expect(controller).toBeDefined();
  });

  it('matches regional HTTP load balancers by urlMapName when routed from create/edit', function () {
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'loadBalancers',
      lazy: true,
      defaultData: [],
    });
    const routedLoadBalancer = {
      name: 'regional-url-map',
      region: 'us-central1',
      accountId: 'test',
      vpcId: null,
    };
    const normalizedLoadBalancer = {
      name: 'regional-url-map (test/us-central1)',
      urlMapName: 'regional-url-map',
      provider: 'gce',
      account: 'test',
      region: 'us-central1',
      loadBalancerType: 'EXTERNAL_MANAGED',
      listeners: [{ name: 'regional-listener' }],
      defaultService: { name: 'backend-service', healthCheck: { name: 'hc' } },
      hostRules: [],
    };
    app.loadBalancers.data.push(normalizedLoadBalancer);

    window.inject(function ($controller, $rootScope, $q) {
      const scope = $rootScope.$new();
      const detailsController = $controller('gceLoadBalancerDetailsCtrl', {
        $scope: scope,
        loadBalancer: routedLoadBalancer,
        app: app,
        $state: $state,
        loadBalancerReader: {
          getLoadBalancerDetails: () =>
            $q.resolve([
              {
                dnsname: '1.2.3.4',
                listenerDescriptions: [{ listener: { loadBalancerPort: '80' } }],
              },
            ]),
        },
      });

      scope.$digest();

      expect(detailsController).toBeDefined();
      expect(scope.loadBalancer).toBe(normalizedLoadBalancer);
    });
  });

  it('uses forwarding rule and backend service log resource types for regional external network load balancers', function () {
    const app = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'loadBalancers',
      lazy: true,
      defaultData: [],
    });
    const regionalExternalNetworkLoadBalancer = {
      name: 'regional-external-network-lb',
      provider: 'gce',
      account: 'test',
      accountId: 'test',
      region: 'us-central1',
      vpcId: null,
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      backendService: { name: 'backend-service', healthCheck: { name: 'tcp-hc' } },
    };
    app.loadBalancers.data.push(regionalExternalNetworkLoadBalancer);

    window.inject(function ($controller, $rootScope, $q) {
      const scope = $rootScope.$new();
      const detailsController = $controller('gceLoadBalancerDetailsCtrl', {
        $scope: scope,
        loadBalancer: regionalExternalNetworkLoadBalancer,
        app: app,
        $state: $state,
        loadBalancerReader: {
          getLoadBalancerDetails: () =>
            $q.resolve([
              {
                dnsname: '35.1.2.3',
                listenerDescriptions: [{ listener: { loadBalancerPort: '80' } }],
              },
            ]),
        },
      });

      scope.$digest();

      expect(detailsController).toBeDefined();
      expect(scope.loadBalancer.logsLink).toContain('gce_forwarding_rule OR gce_backend_service');
    });
  });
});
