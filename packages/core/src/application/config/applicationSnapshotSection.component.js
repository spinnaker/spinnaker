'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import { ConfirmationModalService } from '../../confirmationModal/confirmationModal.service';
import { SnapshotWriter } from '../../snapshot/SnapshotWriter';
import { CORE_SNAPSHOT_DIFF_VIEWSNAPSHOTDIFFBUTTON_COMPONENT } from '../../snapshot/diff/viewSnapshotDiffButton.component';

export const CORE_APPLICATION_CONFIG_APPLICATIONSNAPSHOTSECTION_COMPONENT =
  'spinnaker.core.application.config.serialize.component';
export const name = CORE_APPLICATION_CONFIG_APPLICATIONSNAPSHOTSECTION_COMPONENT; // for backwards compatibility
module(CORE_APPLICATION_CONFIG_APPLICATIONSNAPSHOTSECTION_COMPONENT, [
  UIROUTER_ANGULARJS,
  CORE_SNAPSHOT_DIFF_VIEWSNAPSHOTDIFFBUTTON_COMPONENT,
]).component('applicationSnapshotSection', {
  templateUrl: require('./applicationSnapshotSection.component.html'),
  bindings: {
    application: '=',
  },
  controller: [
    '$state',
    function ($state) {
      if (this.application.notFound || this.application.hasError) {
        return;
      }

      this.takeSnapshot = () => {
        const submitMethod = () => {
          return SnapshotWriter.takeSnapshot(this.application.attributes);
        };

        const taskMonitor = {
          application: this.application,
          title: 'Taking snapshot of ' + this.application.name,
        };

        ConfirmationModalService.confirm({
          header: 'Are you sure you want to take a snapshot of: ' + this.application.name + '?',
          buttonText: 'Take snapshot',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };
    },
  ],
});
