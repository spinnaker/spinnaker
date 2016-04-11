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
      require('./loadBalancerDetail.controller'),
      require('../../../core/application/service/applications.read.service')
    )
  );

  beforeEach(
    window.inject(
      function($controller, $rootScope, _$state_, applicationReader) {
        $scope = $rootScope.$new();
        $state = _$state_;
        let app = {};
        applicationReader.addSectionToApplication({key: 'loadBalancers', lazy: true}, app);
        app.loadBalancers.data.push(loadBalancer);
        controller = $controller('awsLoadBalancerDetailsCtrl', {
          $scope: $scope,
          loadBalancer: loadBalancer,
          app: app,
          $state: $state
        });
      }
    )
  );


  it('should have an instantiated controller', function () {
    expect(controller).toBeDefined();
  });

  describe('Get the first subnets purpose', function () {
    it('should return empty string if there are no subnets ', function () {
      let subnetDetails = [];
      let result = controller.getFirstSubnetPurpose(subnetDetails);
      expect(result).toEqual('');
    });

    it('should return empty string if no subnetDetail is submitted', function () {
      let result = controller.getFirstSubnetPurpose();
      expect(result).toEqual('');
    });

    it('should return empty string if undefined subnetDetail is submitted', function () {
      let result = controller.getFirstSubnetPurpose(undefined);
      expect(result).toEqual('');
    });

    it('should return the first purpose of subnetDetail if there is only one', function () {
      let subnetDetails = [{purpose:'internal(vpc0)'}];
      let result = controller.getFirstSubnetPurpose(subnetDetails);
      expect(result).toEqual('internal(vpc0)');
    });

    it('should return the first purpose of subnetDetail if there are multiple', function () {
      let subnetDetails = [{purpose:'internal(vpc0)'}, {purpose:'internal(vpc1)'}];
      let result = controller.getFirstSubnetPurpose(subnetDetails);
      expect(result).toEqual('internal(vpc0)');
    });
  });

});
