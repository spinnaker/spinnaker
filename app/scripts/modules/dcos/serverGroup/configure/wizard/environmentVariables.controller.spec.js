'use strict';

describe('dcosServerGroupEnvironmentVariablesController', function () {
  var controller;
  var scope;

  beforeEach(window.module(require('./environmentVariables.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.command = {};
      scope.command.secrets = [];
      scope.command.viewModel = {};

      controller = $controller('dcosServerGroupEnvironmentVariablesController', {
        $scope: scope,
      });
    }),
  );

  describe('Environment Variables', function () {
    beforeEach(function () {
      scope.command.env = {};
      scope.command.secrets = [];
      scope.command.viewModel.env = [];
    });

    it('Environment Variables spec 1', function () {
      controller.addEnvironmentVariable();

      expect(scope.command.viewModel.env.length).toEqual(1);
    });

    it('Environment Variables spec 2', function () {
      var index = 0;

      controller.addEnvironmentVariable();

      scope.command.viewModel.env[index].name = 'Key';
      scope.command.viewModel.env[index].value = 'Value';
      scope.command.viewModel.env[index].rawValue = scope.command.viewModel.env[index].value;
      scope.command.viewModel.env[index].isSecret = true;

      controller.addSecret(index);
      controller.synchronize();

      expect(scope.command.viewModel.env.length).toEqual(1);
      expect(scope.command.env['Key']).toEqual({ secret: 'secret0' });

      expect(Object.keys(scope.command.secrets).length).toEqual(1);
    });

    it('Environment Variables spec 3', function () {
      var index = 0;

      controller.addEnvironmentVariable();

      scope.command.viewModel.env[index].name = 'Key';
      scope.command.viewModel.env[index].value = 'Value';
      scope.command.viewModel.env[index].rawValue = scope.command.viewModel.env[index].value;
      scope.command.viewModel.env[index].isSecret = true;

      controller.addSecret(index);
      controller.removeSecret(index);
      controller.synchronize();

      expect(scope.command.viewModel.env.length).toEqual(1);
      expect(scope.command.env['Key']).toEqual(null);

      expect(Object.keys(scope.command.secrets).length).toEqual(0);
    });

    it('Environment Variables spec 4', function () {
      var index = 0;

      controller.addEnvironmentVariable();

      scope.command.viewModel.env[index].name = 'Key';
      scope.command.viewModel.env[index].value = 'oldValue';
      scope.command.viewModel.env[index].rawValue = 'newValue';

      controller.updateValue(index);

      expect(scope.command.viewModel.env[index].value).toEqual(scope.command.viewModel.env[index].rawValue);
    });

    it('Environment Variables spec 5', function () {
      var index = 0;

      controller.addEnvironmentVariable();

      scope.command.viewModel.env[index].name = 'Key';
      scope.command.viewModel.env[index].value = 'oldValue';
      scope.command.viewModel.env[index].rawValue = scope.command.viewModel.env[index].value;
      scope.command.viewModel.env[index].isSecret = true;

      controller.addSecret(index);
      controller.updateValue(index);

      expect(scope.command.secrets['secret' + index].source).toEqual(scope.command.viewModel.env[index].rawValue);
    });

    it('Environment Variables spec 6', function () {
      controller.addEnvironmentVariable();
      controller.removeEnvironmentVariable(0);

      expect(scope.command.viewModel.env.length).toEqual(0);
    });

    it('Environment Variables spec 7', function () {
      var index = 0;

      controller.addEnvironmentVariable();

      scope.command.viewModel.env[index].name = 'Key';
      scope.command.viewModel.env[index].value = 'oldValue';
      scope.command.viewModel.env[index].rawValue = scope.command.viewModel.env[index].value;
      scope.command.viewModel.env[index].isSecret = false;

      controller.updateSecret(index, scope.command.viewModel.env[index].isSecret);

      expect(scope.command.viewModel.env.length).toEqual(1);
      expect(Object.keys(scope.command.secrets).length).toEqual(1);

      scope.command.viewModel.env[index].isSecret = true;

      controller.updateSecret(index, scope.command.viewModel.env[index].isSecret);

      expect(scope.command.viewModel.env.length).toEqual(1);
      expect(Object.keys(scope.command.secrets).length).toEqual(0);
    });
  });
});
