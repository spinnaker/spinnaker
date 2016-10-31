'use strict';

function restoreSnapshotJob ({ appName, cloudProvider, account, timestamp }) {
  return {
    applicationName: appName,
    cloudProvider: cloudProvider,
    credentials: account,
    snapshotTimestamp: timestamp,
    type: 'restoreSnapshot',
    user: '[anonymous]',
  };
}

module.exports = restoreSnapshotJob;
