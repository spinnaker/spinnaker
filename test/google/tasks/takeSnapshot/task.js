'use strict';

let job = require('./job.js'),
  { account, cloudProvider } = require('../../config.json');

function takeSnapshotTask (appName) {
  let task = {
    application: appName,
    description: `Take Snapshot of ${appName}`,
    job: [job({ appName, cloudProvider, account })],
  };
  return task;
}

module.exports = takeSnapshotTask;
