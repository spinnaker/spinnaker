'use strict';

describe('dcosServerGroupVolumesController', function () {
  var controller;
  var scope;

  beforeEach(window.module(require('./volumes.controller').name));

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();

      scope.command = {
        persistentVolumes: [],
        dockerVolumes: [],
        externalVolumes: [],
      };

      controller = $controller('dcosServerGroupVolumesController', {
        $scope: scope,
      });
    }),
  );

  describe('Persistent Volumes', function () {
    beforeEach(function () {
      scope.command.persistentVolumes = [];
    });

    it('Persistent Volumes spec 1', function () {
      controller.addPersistentVolume();

      expect(scope.command.persistentVolumes.length).toEqual(1);
    });

    it('Persistent Volumes spec 2', function () {
      controller.addPersistentVolume();
      controller.removePersistentVolume(0);

      expect(scope.command.persistentVolumes.length).toEqual(0);
    });
  });

  describe('Docker Volumes', function () {
    beforeEach(function () {
      scope.command.dockerVolumes = [];
    });

    it('Docker Volumes spec 1', function () {
      controller.addDockerVolume();

      expect(scope.command.dockerVolumes.length).toEqual(1);
    });

    it('Docker Volumes spec 2', function () {
      controller.addDockerVolume();
      controller.removeDockerVolume(0);

      expect(scope.command.dockerVolumes.length).toEqual(0);
    });
  });

  describe('External Volumes', function () {
    beforeEach(function () {
      scope.command.externalVolumes = [];
    });

    it('External Volumes spec 1', function () {
      controller.addExternalVolume();

      expect(scope.command.externalVolumes.length).toEqual(1);
    });

    it('External Volumes spec 2', function () {
      controller.addExternalVolume();
      controller.removeExternalVolume(0);

      expect(scope.command.externalVolumes.length).toEqual(0);
    });
  });
});
