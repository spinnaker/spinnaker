'use strict';

let request = require('request-promise'),
  { gateUrl } = require('../config.json');

let statusMap = {
  'RUNNING': (appName, taskId, taskName, responseTransform) => timeout(1000)
    .then(() => waitForTaskCompletion({ appName, taskId, taskName, responseTransform })),
  'SUCCEEDED': (appName, taskId) => ({ appName, taskId }),
  'TERMINAL': (appName, taskId) => { throw new Error(`Task ${taskId} failed`) },
  'NOT_STARTED': (appName, taskId, taskName, responseTransform) => timeout(1000)
    .then(() => waitForTaskCompletion({ appName, taskId, taskName, responseTransform }))
};

function waitForTaskCompletion ({ appName, taskId, taskName, responseTransform }) {
  let uri = `${gateUrl}/applications/${appName}${taskId}`;
  let config = {
    method: 'GET',
    json: true,
    uri: uri
  };

  console.log(`Monitoring ${appName}:${taskId}`);

  return request(config)
    .then((response) => {
      response = responseTransform(response);
      console.log(`Task ${taskName} status: ${response.status}`);
      return statusMap[response.status](appName, taskId, taskName, responseTransform);
    });
}

function timeout (time) {
  return new Promise((resolve) => {
    setTimeout(() => resolve(), time);
  });
}

module.exports = waitForTaskCompletion;
