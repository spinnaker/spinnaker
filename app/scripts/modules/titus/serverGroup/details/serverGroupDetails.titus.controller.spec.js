'use strict';

describe('Controller: titusServerGroupDetailsCtrl', function () {
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./serverGroupDetails.titus.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('titusServerGroupDetailsCtrl', {
        $scope: scope,
        app: {
          serverGroups: {data: []},
          loadBalancers: {data: []},
        },
        serverGroup: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

