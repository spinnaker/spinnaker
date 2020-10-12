'use strict';

describe('dcosServerGroupContainerSettingsController', function () {
  var controller;
  var scope;

  beforeEach(window.module(require('./containerSettings.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.command = {};

      controller = $controller('dcosServerGroupContainerSettingsController', {
        $scope: scope,
        dcosServerGroupConfigurationService: undefined,
      });
    }),
  );

  describe('Group By Registry', function () {
    beforeEach(function () {
      scope.command.env = [];
    });

    it('Group By Registry spec 1', function () {
      var image = {
        fromContext: true,
      };

      var result = controller.groupByRegistry(image);
      var expected = 'Find Image Result(s)';

      expect(result).toEqual(expected);
    });

    it('Group By Registry spec 2', function () {
      var image = {
        fromTrigger: true,
      };

      var result = controller.groupByRegistry(image);
      var expected = 'Images from Trigger(s)';

      expect(result).toEqual(expected);
    });

    it('Group By Registry spec 3', function () {
      var image = {
        registry: 'registry',
      };

      var result = controller.groupByRegistry(image);
      var expected = image.registry;

      expect(result).toEqual(expected);
    });
  });
});
