'use strict';

const angular = require('angular');

import { CONFIRMATION_MODAL_SERVICE } from 'core/confirmationModal/confirmationModal.service';
import { SnapshotWriter } from 'core/snapshot/SnapshotWriter';

module.exports = angular
  .module('spinnaker.core.application.config.serialize.component', [
    require('@uirouter/angularjs').default,
    CONFIRMATION_MODAL_SERVICE,
    require('core/snapshot/diff/viewSnapshotDiffButton.component').name,
  ])
  .component('applicationSnapshotSection', {
    templateUrl: require('./applicationSnapshotSection.component.html'),
    bindings: {
      application: '=',
    },
    controller: ['$state', 'confirmationModalService', function($state, confirmationModalService) {
      if (this.application.notFound) {
        return;
      }

      this.takeSnapshot = () => {
        var submitMethod = () => {
          return SnapshotWriter.takeSnapshot(this.application.attributes);
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
          submitMethod: submitMethod,
        });
      };
    }],
  });
