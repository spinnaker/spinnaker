'use strict';

describe('Controller: CreateApplicationModalCtrl', function () {
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(window.module(require('./createApplication.modal.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('CreateApplicationModalCtrl', {
        $scope: scope,
        $uibModalInstance: {},
      });
    }),
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});
