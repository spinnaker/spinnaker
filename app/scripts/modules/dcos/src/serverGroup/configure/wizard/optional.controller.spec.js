'use strict';

describe('dcosServerGroupOptionalController', function () {
  var controllerFactory;
  var controller;
  var scope;

  beforeEach(window.module(require('./optional.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.command = {
        viewModel: {},
      };

      controllerFactory = $controller;
      controller = controllerFactory('dcosServerGroupOptionalController', {
        $scope: scope,
      });
    }),
  );

  describe('Initialize fetch viewModel', function () {
    it('The viewModel is null if there is no existing fetch model', function () {
      expect(scope.command.viewModel.fetch).toBe(null);
    });

    it('The viewModel is initialized if there is an existing fetch model', function () {
      scope.command.fetch = [{ uri: 'http://url1.com' }, { uri: 'http://url2.com' }];

      controllerFactory('dcosServerGroupOptionalController', {
        $scope: scope,
      });

      expect(scope.command.viewModel.fetch).toEqual('http://url1.com,http://url2.com');
    });

    it('Synchronizes the viewModel to the model', function () {
      scope.command.viewModel.fetch = 'http://url1.com,http://url2.com';
      controller.synchronize();
      expect(scope.command.fetch).toEqual([{ uri: 'http://url1.com' }, { uri: 'http://url2.com' }]);
    });
  });
});
