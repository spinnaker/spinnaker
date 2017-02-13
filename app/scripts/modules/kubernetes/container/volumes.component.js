'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.kubernetes.volumes.component', [])
  .component('kubernetesContainerVolumes', {
    bindings: {
      volumeSources: '=',
      volumeMounts: '=',
    },
    templateUrl: require('./volumes.component.html'),
    controller: function () {
      ['volumeMounts', 'volumeSources'].forEach((prop) => {
        if (!this[prop]) {
          this[prop] = [];
        }
      });

      this.volumeTypes = ['CONFIGMAP', 'EMPTYDIR', 'HOSTPATH', 'PERSISTENTVOLUMECLAIM', 'SECRET'];
      this.mediumTypes = ['DEFAULT', 'MEMORY'];
      this.pathPattern = '^/.*$';
      this.relativePathPattern = '^[^/].*';

      this.defaultHostPath = () => {
        return {
          path: '/',
        };
      };

      this.defaultEmptyDir = () => {
        return {
          medium: this.mediumTypes[0],
        };
      };

      this.defaultSecret = () => {
        return {
          secretName: '',
        };
      };

      this.defaultPersistentVolumeClaim = () => {
        return {
          claimName: '',
          readOnly: true
        };
      };

      this.defaultConfigMap = () => {
        return {
          configMapName: '',
          items: [this.defaultItem()],
        };
      };

      this.defaultItem = () => {
        return {
          key: '',
          path: '',
        };
      };

      this.addItem = (sourceIndex) => {
        this.volumeSources[sourceIndex].configMap.items.push(this.defaultItem());
      };

      this.removeItem = (sourceIndex, itemIndex) => {
        this.volumeSources[sourceIndex].configMap.items.splice(itemIndex, 1);
      };

      this.defaultVolumeSource = (name = '') => {
        return {
          type: this.volumeTypes[0],
          name: name,
          hostPath: this.defaultHostPath(),
          emptyDir: this.defaultEmptyDir(),
          defaultPersistentVolumeClaim: this.defaultPersistentVolumeClaim(),
          secret: this.defaultSecret(),
          configMap: this.defaultConfigMap(),
        };
      };

      this.defaultVolumeMount = (name = '') => {
        return { name: name, readOnly: false, mountPath: '/', };
      };

      this.addVolume = () => {
        let name = Date.now().toString();
        this.volumeMounts.push(this.defaultVolumeMount(name));
        this.volumeSources.push(this.defaultVolumeSource(name));
      };

      this.removeVolume = (index) => {
        this.volumeSources.splice(index, 1);
        this.volumeMounts.splice(index, 1);
      };

      this.prepVolumes = () => {
        this.volumeSources = this.volumeSources || [];
        this.volumeSources.map((source) => {
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

          return source;
        });
      };

      this.prepVolumes();
    }
  });
