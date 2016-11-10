'use strict';

import _ from 'lodash';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.snapshot.write.service', [
    require('../task/taskExecutor.js'),
    ACCOUNT_SERVICE,
    require('../cloudProvider/cloudProvider.registry.js'),
  ])
  .factory('snapshotWriter', function($q, taskExecutor, cloudProviderRegistry,
                                      accountService) {

    function buildSaveSnapshotJobs(app, accountDetails) {
      let jobs = [];
      accountDetails.forEach((accountDetail) => {
        if (cloudProviderRegistry.getValue(accountDetail.cloudProvider, 'snapshotsEnabled')) {
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
          if (cloudProviderRegistry.getValue(accountDetail.cloudProvider, 'snapshotsEnabled')) {
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
      let accountDetailPromises = accounts.map(account => accountService.getAccountDetails(account));
      return $q.all(accountDetailPromises);
    }

    function takeSnapshot(app) {
      return loadAccountDetails(app).then(function(accountDetails) {
        let jobs = buildSaveSnapshotJobs(app, accountDetails);
        return taskExecutor.executeTask({
          job: jobs,
          application: app,
          description: 'Take Snapshot of ' + app.name
        });
      });
    }

    function restoreSnapshot(app, account, timestamp) {
      return accountService.getAccountDetails(account).then(function(accountDetail) {
        let jobs = buildRestoreSnapshotJob(app, accountDetail, timestamp);
        return taskExecutor.executeTask({
          job: jobs,
          application: app,
          description: `Restore Snapshot ${timestamp} of application: ${app.name} for account: ${accountDetail.name}`
        });
      });
    }

    return { takeSnapshot,
             restoreSnapshot};

  });
