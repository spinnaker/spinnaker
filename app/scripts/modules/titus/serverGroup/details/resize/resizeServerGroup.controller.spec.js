'use strict';

describe('Controller: titusResizeServerGroupCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    window.module(
      require('./resizeServerGroup.controller')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('titusResizeServerGroupCtrl', {
        $scope: scope,
        $uibModalInstance: { result: {then: angular.noop}},
        application: {},
        serverGroup: {
          capacity:{
            min:0,
            max:0,
            desired:0
          }
        }
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});


