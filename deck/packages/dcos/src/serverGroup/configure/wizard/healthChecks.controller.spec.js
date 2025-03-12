'use strict';

describe('dcosServerGroupHealthChecksController', function () {
  var controller;
  var scope;

  beforeEach(window.module(require('./healthChecks.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.command = {
        healthChecks: [],
      };

      controller = $controller('dcosServerGroupHealthChecksController', {
        $scope: scope,
      });
    }),
  );

  describe('Health Checks', function () {
    beforeEach(function () {
      scope.command.healthChecks = [];
    });

    it('Health Checks spec 1', function () {
      controller.addHealthCheck();

      expect(scope.command.healthChecks.length).toEqual(1);
    });

    it('Health Checks spec 2', function () {
      controller.addHealthCheck();
      controller.removeHealthCheck(0);

      expect(scope.command.healthChecks.length).toEqual(0);
    });
  });
});
