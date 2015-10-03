'use strict';

describe('Controller: titanServerGroupDetailsCtrl', function () {
  const angular = require('angular');

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./serverGroupDetails.titan.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('titanServerGroupDetailsCtrl', {
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

