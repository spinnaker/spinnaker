'use strict';

describe('Controller: gceServerGroupDetailsCtrl', function () {
  const angular = require('angular');

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./serverGroupDetails.gce.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('gceServerGroupDetailsCtrl', {
        $scope: scope,
        app: {
          serverGroups: [],
          loadBalancers: [],
          registerAutoRefreshHandler: angular.noop
        },
        serverGroup: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

