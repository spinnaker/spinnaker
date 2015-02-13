'use strict';

describe('Controller: ApplicationCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('deckApp.application.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('ApplicationCtrl', {
        $scope: scope,
        application: {
          enableAutoRefresh: angular.noop
        }
      });
    })
  );


  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});

