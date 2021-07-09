import { ApplicationModelBuilder } from '@spinnaker/core';

describe('Controller: azureLoadBalancerDetailsCtrl', function () {
  var controller;
  var $scope;
  var $state;
  var loadBalancer = {
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
      let app = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'loadBalancers',
        lazy: true,
        defaultData: [],
      });
      app.loadBalancers.data.push(loadBalancer);
      controller = $controller('azureLoadBalancerDetailsCtrl', {
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
