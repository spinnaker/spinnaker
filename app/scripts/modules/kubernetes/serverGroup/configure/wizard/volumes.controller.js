'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.volumes', [
])
  .controller('kubernetesServerGroupVolumesController', function($scope) {
    this.volumeTypes = ['EMPTYDIR', 'HOSTPATH', 'PERSISTENTVOLUMECLAIM', 'SECRET'];
    this.mediumTypes = ['DEFAULT', 'MEMORY'];
    this.pathPattern = '^/.*$';

    this.defaultHostPath = function() {
      return {
        path: '/',
      };
    };

    this.defaultEmptyDir = function() {
      return {
        medium: this.mediumTypes[0],
      };
    };

    this.defaultSecret = function() {
      return {
        secretName: '',
      };
    };

    this.defaultPersistentVolumeClaim = function() {
      return {
        claimName: '',
        readOnly: true
      };
    };

    this.defaultVolume = function() {
      return {
        type: this.volumeTypes[0],
        name: '',
        hostPath: this.defaultHostPath(),
        emptyDir: this.defaultEmptyDir(),
        defaultPersistentVolumeClaim: this.defaultPersistentVolumeClaim(),
        secret: this.defaultSecret(),
      };
    };

    this.addVolume = function() {
      $scope.command.volumeSources.push(this.defaultVolume());
    };

    this.removeVolume = function(index) {
      $scope.command.volumeSources.splice(index, 1);
    };

    this.prepVolumes = function() {
      $scope.command.volumeSources = $scope.command.volumeSources || [];
      $scope.command.volumeSources.map((source) => {
        if (!source.hostPath) {
          source.hostPath = this.defaultHostPath();
        }

        if (!source.emptyDir) {
          source.emptyDir = this.defaultEmptyDir();
        }

        if (!source.persistentVolumeClaim) {
          source.persistentVolumeClaim = this.defaultPersistentVolumeClaim();
        }

        if (!source.secret) {
          source.secret = this.defaultSecret();
        }

        return source;
      });
    };

    this.prepVolumes();
  });
