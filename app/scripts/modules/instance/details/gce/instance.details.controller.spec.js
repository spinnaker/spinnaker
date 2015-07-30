'use strict';

describe('Controller: gceInstanceDetailsCtrl', function () {

  const angular = require('angular');
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./instance.details.controller.js')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('gceInstanceDetailsCtrl', {
        $scope: scope,
        instance: {},
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
