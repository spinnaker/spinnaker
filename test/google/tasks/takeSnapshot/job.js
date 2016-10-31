'use strict';

function takeSnapshotJob ({ account, cloudProvider, appName }) {
  return {
    applicationName: appName,
    cloudProvider: cloudProvider,
    credentials: account,
    type: 'saveSnapshot',
    user: '[anonymous]',
  };
}

module.exports = takeSnapshotJob;
