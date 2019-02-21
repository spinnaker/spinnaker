'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.configure.kubernetes.volumes', [])
  .controller('kubernetesServerGroupVolumesController', ['$scope', function($scope) {
    this.volumeTypes = [
      'CONFIGMAP',
      'EMPTYDIR',
      'HOSTPATH',
      'PERSISTENTVOLUMECLAIM',
      'SECRET',
      'AWSELASTICBLOCKSTORE',
      'NFS',
    ];
    this.mediumTypes = ['DEFAULT', 'MEMORY'];
    this.pathPattern = '^/.*$';
    this.relativePathPattern = '^[^/].*';

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
        readOnly: true,
      };
    };

    this.defaultConfigMap = function() {
      return {
        configMapName: '',
        items: [this.defaultItem()],
      };
    };

    this.defaultItem = function() {
      return {
        key: '',
        path: '',
      };
    };

    this.defaultAwsElasticBlockStore = function() {
      return {
        volumeId: '',
        fsType: '',
        partition: 0,
      };
    };

    this.defaultNfs = function() {
      return {
        server: '',
        path: '/',
        readOnly: false,
      };
    };

    this.addItem = function(sourceIndex) {
      $scope.command.volumeSources[sourceIndex].configMap.items.push(this.defaultItem());
    };

    this.removeItem = function(sourceIndex, itemIndex) {
      $scope.command.volumeSources[sourceIndex].configMap.items.splice(itemIndex, 1);
    };

    this.defaultVolume = function() {
      return {
        type: this.volumeTypes[0],
        name: '',
        hostPath: this.defaultHostPath(),
        emptyDir: this.defaultEmptyDir(),
        defaultPersistentVolumeClaim: this.defaultPersistentVolumeClaim(),
        secret: this.defaultSecret(),
        configMap: this.defaultConfigMap(),
        awsElasticBlockStore: this.defaultAwsElasticBlockStore(),
        nfs: this.defaultNfs(),
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
      $scope.command.volumeSources.map(source => {
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

        if (!source.configMap) {
          source.configMap = this.defaultConfigMap();
        }

        if (!source.awsElasticBlockStore) {
          source.awsElasticBlockStore = this.defaultAwsElasticBlockStore();
        }

        if (!source.nfs) {
          source.nfs = this.defaultNfs();
        }

        return source;
      });
    };

    this.prepVolumes();
  }]);
