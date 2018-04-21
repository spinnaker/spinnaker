'use strict';

import _ from 'lodash';
import { AccountService } from 'core/account/AccountService';
import { CloudProviderRegistry } from 'core/cloudProvider';
import { TASK_EXECUTOR } from 'core/task/taskExecutor';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.snapshot.write.service', [TASK_EXECUTOR])
  .factory('snapshotWriter', function($q, taskExecutor) {
    function buildSaveSnapshotJobs(app, accountDetails) {
      let jobs = [];
      accountDetails.forEach(accountDetail => {
        if (CloudProviderRegistry.getValue(accountDetail.cloudProvider, 'snapshotsEnabled')) {
          jobs.push({
            type: 'saveSnapshot',
            credentials: accountDetail.name,
            applicationName: app.name,
            cloudProvider: accountDetail.cloudProvider,
          });
        }
      });
      return jobs;
    }

    function buildRestoreSnapshotJob(app, accountDetail, timestamp) {
      let jobs = [];
      if (CloudProviderRegistry.getValue(accountDetail.cloudProvider, 'snapshotsEnabled')) {
        jobs.push({
          type: 'restoreSnapshot',
          credentials: accountDetail.name,
          applicationName: app.name,
          snapshotTimestamp: timestamp,
          cloudProvider: accountDetail.cloudProvider,
        });
      }
      return jobs;
    }

    function loadAccountDetails(app) {
      let accounts = _.isString(app.accounts) ? app.accounts.split(',') : [];
      let accountDetailPromises = accounts.map(account => AccountService.getAccountDetails(account));
      return $q.all(accountDetailPromises);
    }

    function takeSnapshot(app) {
      return loadAccountDetails(app).then(function(accountDetails) {
        let jobs = buildSaveSnapshotJobs(app, accountDetails);
        return taskExecutor.executeTask({
          job: jobs,
          application: app,
          description: 'Take Snapshot of ' + app.name,
        });
      });
    }

    function restoreSnapshot(app, account, timestamp) {
      return AccountService.getAccountDetails(account).then(function(accountDetail) {
        let jobs = buildRestoreSnapshotJob(app, accountDetail, timestamp);
        return taskExecutor.executeTask({
          job: jobs,
          application: app,
          description: `Restore Snapshot ${timestamp} of application: ${app.name} for account: ${accountDetail.name}`,
        });
      });
    }

    return {
      takeSnapshot,
      restoreSnapshot,
    };
  });
