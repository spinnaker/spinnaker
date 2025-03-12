'use strict';

describe('dcosServerGroupNetworkController', function () {
  var controller;
  var scope;

  beforeEach(window.module(require('./network.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.command = {
        serviceEndpoints: [],
      };

      controller = $controller('dcosServerGroupNetworkController', {
        $scope: scope,
      });
    }),
  );

  describe('Service Endpoints', function () {
    beforeEach(function () {
      scope.command.serviceEndpoints = [];
    });

    it('Service Endpoints spec 1', function () {
      controller.addServiceEndpoint();

      expect(scope.command.serviceEndpoints.length).toEqual(1);
    });

    it('Service Endpoints spec 2', function () {
      controller.addServiceEndpoint();
      controller.removeServiceEndpoint(0);

      expect(scope.command.serviceEndpoints.length).toEqual(0);
    });

    it('Service Endpoints spec 3', function () {
      controller.addServiceEndpoint();
      controller.addServiceEndpoint();
      controller.addServiceEndpoint();

      scope.command.networkType = controller.networkTypes[1];
      controller.changeNetworkType();

      scope.command.serviceEndpoints.forEach(function (endpoint) {
        expect(endpoint.networkType).toEqual(scope.command.networkType);
      });
    });
  });
});
