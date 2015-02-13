'use strict';

describe('Controller: InstanceDetailsCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('deckApp.instance.detail.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('InstanceDetailsCtrl', {
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
