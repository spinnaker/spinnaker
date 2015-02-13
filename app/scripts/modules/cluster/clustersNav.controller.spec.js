'use strict';

describe('Controller: ClustersNavCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('deckApp.clusterNav.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.sortFilter = {};

      controller = $controller('ClustersNavCtrl', {
        $scope: scope,
        application: {}
      });
    })
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});


