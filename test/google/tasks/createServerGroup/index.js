'use strict';

let task = require('./task'),
  taskName = 'createServerGroup';

function responseTransform (response) {
  return response.steps.find((step) => step.name === 'monitorDeploy');
}

module.exports = { task, responseTransform, taskName };
