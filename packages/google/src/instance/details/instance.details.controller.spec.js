'use strict';

describe('Controller: gceInstanceDetailsCtrl', function () {
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  let controller;
  let scope;

  beforeEach(window.module(require('./instance.details.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('gceInstanceDetailsCtrl', {
        $scope: scope,
        instance: {},
        moniker: {},
        environment: 'test',
        app: { isStandalone: true },
      });
    }),
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});
