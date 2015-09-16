'use strict';

describe('Controller: LoadBalancerDetailsCtrl', function () {
  const angular = require('angular');

  //NOTE: This is just a skeleton test to test DI.  Please add more tests.;

  var controller;
  var $scope;
  var $state;
  var loadBalancer = {
    name: 'foo',
    region: 'us-west-1',
    account: 'test',
    accountId: 'test',
    vpcId: '1'
  };


  beforeEach(
    window.module(
      require('./loadBalancerDetail.controller')
    )
  );

  beforeEach(
    window.inject(
      function($controller, $rootScope, _$state_) {
        $scope = $rootScope.$new();
        $state = _$state_;
        controller = $controller('awsLoadBalancerDetailsCtrl', {
          $scope: $scope,
          loadBalancer: loadBalancer,
          app: {
            loadBalancers:[loadBalancer],
            registerAutoRefreshHandler: angular.noop
          },
          $state: $state
        });
      }
    )
  );


  it('should have an instantiated controller', function () {
    expect(controller).toBeDefined();
  });

});
