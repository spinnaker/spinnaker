'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.serialize.component', [
    require('angular-ui-router'),
    require('./../../snapshot/snapshot.write.service.js'),
    require('../../confirmationModal/confirmationModal.service.js'),
    require('../../../core/snapshot/diff/viewSnapshotDiffButton.component.js'),
  ])
  .component('applicationSnapshotSection', {
    templateUrl: require('./applicationSnapshotSection.component.html'),
    bindings: {
      application: '=',
    },
    controller: function ($state, snapshotWriter, confirmationModalService) {
      if (this.application.notFound) {
        return;
      }

      this.takeSnapshot = () => {

        var submitMethod = () => {
          return snapshotWriter.takeSnapshot(this.application.attributes);
        };

        var taskMonitor = {
          application: this.application,
          title: 'Taking snapshot of ' + this.application.name,
          hasKatoTask: true,
        };

        confirmationModalService.confirm({
          header: 'Are you sure you want to take a snapshot of: ' + this.application.name + '?',
          buttonText: 'Take snapshot',
          provider: 'gce',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod
        });
      };
    }
  });
