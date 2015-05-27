'use strict';

describe('Controller: ResizeServerGroupCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('spinnaker.resizeServerGroup.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('ResizeServerGroupCtrl', {
        $scope: scope,
        $modalInstance: {},
        application: {},
        serverGroup: {
          asg:{
            minSize:0
          }
        }
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});


