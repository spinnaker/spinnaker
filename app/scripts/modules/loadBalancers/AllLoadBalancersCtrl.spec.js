'use strict';


describe('Controller: AllLoadBalancerCtrl', function () {

  const angular = require('angular');
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./AllLoadBalancersCtrl.js')
    )
  );

  beforeEach(
    window.inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('AllLoadBalancersCtrl', {
        $scope: scope,
        app: {
          registerAutoRefreshHandler: angular.noop
        }
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});
