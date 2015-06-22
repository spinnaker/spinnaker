'use strict';


describe('Controller: LoadBalancersNavCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./LoadBalancersNavCtrl.js')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('LoadBalancersNavCtrl', {
        $scope: scope,
        app: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

