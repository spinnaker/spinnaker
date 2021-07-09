'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { SnapshotReader } from '../SnapshotReader';
import { SnapshotWriter } from '../SnapshotWriter';
import { ConfirmationModalService } from '../../confirmationModal';
import { DIFF_SUMMARY_COMPONENT } from '../../pipeline/config/actions/history/diffSummary.component';
import { DIFF_VIEW_COMPONENT } from '../../pipeline/config/actions/history/diffView.component';
import { JsonUtils } from '../../utils/json/JsonUtils';

import './snapshotDiff.modal.less';

export const CORE_SNAPSHOT_DIFF_SNAPSHOTDIFF_MODAL_CONTROLLER = 'spinnaker.deck.core.snapshot.diff.modal.controller';
export const name = CORE_SNAPSHOT_DIFF_SNAPSHOTDIFF_MODAL_CONTROLLER; // for backwards compatibility
module(CORE_SNAPSHOT_DIFF_SNAPSHOTDIFF_MODAL_CONTROLLER, [DIFF_SUMMARY_COMPONENT, DIFF_VIEW_COMPONENT]).controller(
  'SnapshotDiffModalCtrl',
  [
    'availableAccounts',
    'application',
    '$filter',
    '$uibModalInstance',
    function (availableAccounts, application, $filter, $uibModalInstance) {
      this.availableAccounts = availableAccounts;
      this.selectedAccount = _.head(availableAccounts);
      this.compareOptions = ['most recent', 'previous version'];
      this.compareTo = _.last(this.compareOptions);
      this.findLeftMap = {
        'most recent': () => _.head(this.snapshots).contents,
        'previous version': (right, version) => {
          let left = right;
          if (version < this.snapshots.length - 1) {
            left = this.snapshots[version + 1].contents;
          }
          return left;
        },
      };

      const resetView = () => {
        this.state = {
          loading: true,
          error: false,
        };
        this.diff = JsonUtils.diff([], []);
        this.snapshots = [];
        this.version = 0;
      };

      const formatSnapshots = (snapshots) => {
        const formatted = snapshots
          .sort((a, b) => b.timestamp - a.timestamp)
          .map((s, index) => {
            return {
              formattedTimestamp: $filter('timestamp')(s.timestamp),
              timestamp: s.timestamp,
              contents: JSON.stringify(s.infrastructure, null, 2),
              json: s.infrastructure,
              index: index,
            };
          });

        _.head(formatted).formattedTimestamp += ' (most recent)';
        return formatted;
      };

      const loadSuccess = (snapshots) => {
        this.state.loading = false;
        if (!snapshots.length) {
          return;
        }

        this.snapshots = formatSnapshots(snapshots);
        this.updateDiff();
      };

      const loadError = () => {
        this.state.loading = false;
        this.state.error = true;
      };

      this.getSnapshotHistoryForAccount = (account) => {
        resetView();
        if (account) {
          SnapshotReader.getSnapshotHistory(application.name, account).then(loadSuccess, loadError);
        } else {
          loadSuccess([]);
        }
      };

      this.restoreSnapshot = () => {
        const submitMethod = () => {
          return SnapshotWriter.restoreSnapshot(
            application,
            this.selectedAccount,
            this.snapshots[this.version].timestamp,
          );
        };

        const taskMonitor = {
          application: application,
          title: 'Restoring snapshot of ' + application.name,
        };

        ConfirmationModalService.confirm({
          header: `Are you sure you want to restore snapshot of: ${application.name}?`,
          buttonText: 'Restore snapshot',
          body: '<p>This will change your infrastructure to the state specified in the snapshot selected</p>',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.updateDiff = () => {
        if (!this.snapshots.length) {
          resetView();
          return;
        }

        this.right = this.snapshots[this.version].contents;
        this.left = this.findLeftMap[this.compareTo](this.right, this.version);
        this.diff = JsonUtils.diff(this.left, this.right);
      };

      this.getSnapshotHistoryForAccount(this.selectedAccount);
      this.close = $uibModalInstance.dismiss;
    },
  ],
);
