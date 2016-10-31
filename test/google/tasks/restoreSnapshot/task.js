'use strict';

let job = require('./job.js'),
  { cloudProvider, account } = require('../../config.json'),
  { getLastSnapshot } = require('../utils/getSnapshots.js');

function restoreSnapshotTask (appName) {
  // TODO(dpeach): make snapshot choice configurable (and revert to last snapshot by default).
  return getLastSnapshot(appName)
    .then((snapshot) => {
      let timestamp = snapshot.timestamp;

      let task = {
        application: appName,
        description: `Restore Snapshot ${timestamp} of application: ${appName} for account: ${account}`,
        job: [job({ appName, cloudProvider, account, timestamp })],
      };

      return task;
    });
}

module.exports = restoreSnapshotTask;
