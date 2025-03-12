import { ApplicationModelBuilder } from '@spinnaker/core';

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
    window.inject(function ($controller, $rootScope, _$state_) {
      $scope = $rootScope.$new();
      $state = _$state_;
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
      });
    }),
  );

  it('should have an instantiated controller', function () {
    expect(controller).toBeDefined();
  });
});
