'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.volumes', [])
  .controller('dcosServerGroupVolumesController', [
    '$scope',
    function($scope) {
      this.volumeModes = [
        {
          mode: 'RW',
          description: 'Read And Write',
        },
        {
          mode: 'RO',
          description: 'Read Only',
        },
      ];

      this.isVolumesValid = function(volumes) {
        return !(volumes === undefined || volumes == null || typeof volumes === 'string' || volumes instanceof String);
      };

      this.addPersistentVolume = function() {
        if (!this.isVolumesValid($scope.command.persistentVolumes)) {
          $scope.command.persistentVolumes = [];
        }

        $scope.command.persistentVolumes.push({
          containerPath: null,
          persistent: {
            size: null,
          },
          mode: this.volumeModes[0].mode,
        });
      };

      this.removePersistentVolume = function(index) {
        $scope.command.persistentVolumes.splice(index, 1);
      };

      this.addDockerVolume = function() {
        if (!this.isVolumesValid($scope.command.dockerVolumes)) {
          $scope.command.dockerVolumes = [];
        }

        $scope.command.dockerVolumes.push({
          containerPath: null,
          hostPath: null,
          mode: this.volumeModes[0].mode,
        });
      };

      this.removeDockerVolume = function(index) {
        $scope.command.dockerVolumes.splice(index, 1);
      };

      this.addExternalVolume = function() {
        if (!this.isVolumesValid($scope.command.externalVolumes)) {
          $scope.command.externalVolumes = [];
        }

        $scope.command.externalVolumes.push({
          containerPath: null,
          external: {
            name: null,
            provider: 'dvdi',
            options: {
              'dvdi/driver': 'rexray',
            },
          },
          mode: this.volumeModes[0].mode,
        });
      };

      this.removeExternalVolume = function(index) {
        $scope.command.externalVolumes.splice(index, 1);
      };
    },
  ]);
