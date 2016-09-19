'use strict';

let job = require('./job.js'),
  { account } = require('../../config.json');

function deleteAppJob (appName) {
  let task = {
    application: appName,
    description: `Deleting Application: ${appName}`,
    job: [job({ account, appName })]
  };

  return task;
}

module.exports = deleteAppJob;
