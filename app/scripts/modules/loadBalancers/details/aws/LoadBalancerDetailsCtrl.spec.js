'use strict';

describe('Controller: LoadBalancerDetailsCtrl', function () {

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
    module(
      'spinnaker.loadBalancer.aws.details.controller',
      'spinnaker.states'
    )
  );

  beforeEach(
    inject(
      function($controller, $rootScope, _$state_) {
        $scope = $rootScope.$new();
        $state = _$state_;
        controller = $controller('awsLoadBalancerDetailsCtrl', {
          $scope: $scope,
          loadBalancer: loadBalancer,
          application: {
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
