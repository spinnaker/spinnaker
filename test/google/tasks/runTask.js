'use strict';

let request = require('request-promise'),
  { gateUrl } = require('../config.json');

function runTask (appName, task) {
  let config = {
    method: 'POST',
    uri: `${gateUrl}/applications/${appName}/tasks`,
    body: task,
    json: true,
  };

  console.log(task.description, `at ${config.uri}`);

  return request(config);
}

module.exports = runTask;
