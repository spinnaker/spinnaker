'use strict';

describe('Controller: openstackResizeServerGroupCtrl', function () {

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
      controller = $controller('openstackResizeServerGroupCtrl', {
        $scope: scope,
        $uibModalInstance: {},
        application: {},
        serverGroup: {
          scalingConfig:{
            min:0
          }
        }
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});


