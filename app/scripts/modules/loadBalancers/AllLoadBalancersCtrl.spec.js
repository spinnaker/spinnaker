'use strict';

describe('Controller: AllLoadBalancerCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('deckApp.loadBalancer.controller')
  );

  beforeEach(
    inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('AllLoadBalancersCtrl', {
        $scope: scope,
        application: {
          registerAutoRefreshHandler: angular.noop
        }
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});
