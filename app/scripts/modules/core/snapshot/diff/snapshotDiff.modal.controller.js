'use strict';

let angular = require('angular');

require('./snapshotDiff.modal.less');

module.exports = angular.module('spinnaker.deck.core.snapshot.diff.modal.controller', [
    require('../../utils/lodash.js'),
    require('../snapshot.read.service.js'),
    require('../snapshot.write.service.js'),
    require('../../confirmationModal/confirmationModal.service.js'),
    require('../../pipeline/config/actions/history/jsonDiff.service.js'),
    require('../../pipeline/config/actions/history/diffSummary.component.js'),
    require('../../pipeline/config/actions/history/diffView.component.js'),
  ])
  .controller('SnapshotDiffModalCtrl', function (availableAccounts, application, _, $filter, $uibModalInstance,
                                                 snapshotReader, snapshotWriter, jsonDiffService, confirmationModalService) {
    this.availableAccounts = availableAccounts;
    this.selectedAccount = _.first(availableAccounts);
    this.compareOptions = ['most recent', 'previous version'];
    this.compareTo = _.last(this.compareOptions);
    this.findLeftMap = {
      'most recent': () => _.first(this.snapshots).contents,
      'previous version': (right, version) => {
        let left = right;
        if (version < this.snapshots.length - 1) {
          left = this.snapshots[version + 1].contents;
        }
        return left;
      }
    };

    let resetView = () => {
      this.state = {
        loading: true,
        error: false
      };
      this.diff = jsonDiffService.diff([], []);
      this.snapshots = [];
      this.version = 0;
    };

    let formatSnapshots = (snapshots) => {
      let formatted = snapshots
        .sort((a, b) => b.timestamp - a.timestamp)
        .map((s, index) => {
          return {
            formattedTimestamp: $filter('timestamp')(s.timestamp),
            timestamp: s.timestamp,
            contents: JSON.stringify(s.infrastructure, null, 2),
            json: s.infrastructure,
            index: index
          };
        });

      _.first(formatted).formattedTimestamp += ' (most recent)';
      return formatted;
    };

    let loadSuccess = (snapshots) => {
      this.state.loading = false;
      if (!snapshots.length) {
        return;
      }

      this.snapshots = formatSnapshots(snapshots);
      this.updateDiff();
    };

    let loadError = () => {
      this.state.loading = false;
      this.state.error = true;
    };

    this.getSnapshotHistoryForAccount = (account) => {
      resetView();
      if (account) {
        snapshotReader.getSnapshotHistory(application.name, account)
          .then(loadSuccess, loadError);
      } else {
        loadSuccess([]);
      }
    };

    this.restoreSnapshot = () => {
      let submitMethod = () => {
        return snapshotWriter.restoreSnapshot(application, this.selectedAccount, this.snapshots[this.version].timestamp);
      };

      let taskMonitor = {
        application: application,
        title: 'Restoring snapshot of ' + application.name,
        hasKatoTask: true,
      };

      confirmationModalService.confirm({
        header: `Are you sure you want to restore snapshot of: ${application.name}?`,
        buttonText: 'Restore snapshot',
        provider: 'gce',
        body: '<p>This will change your infrastructure to the state specified in the snapshot selected</p>',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.updateDiff = () => {
      if (!this.snapshots.length) {
        resetView();
        return;
      }

      this.right = this.snapshots[this.version].contents;
      this.left = this.findLeftMap[this.compareTo](this.right, this.version);
      this.diff = jsonDiffService.diff(this.left, this.right);
    };

    this.getSnapshotHistoryForAccount(this.selectedAccount);
    this.close = $uibModalInstance.dismiss;
  });
