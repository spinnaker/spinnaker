'use strict';

describe('Controller: ClusterCtrl', function () {

  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(
    module('deckApp.cluster.controller')
  );

  beforeEach(
    inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      controller = $controller('ClusterCtrl', {
        $scope: scope,
        cluster: {},
        application: {
          getCluster: function() {
            return {
              serverGroups: []
            };
          },
          registerAutoRefreshHandler: angular.noop
        }
      });

    })
  );

  it('should instantiate the controller', function () {

    expect(controller).toBeDefined();
  });
});

