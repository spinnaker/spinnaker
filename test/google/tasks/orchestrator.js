'use strict';

let runTask = require('./runTask'),
  monitorTask = require('./monitorTask');

function runAndMonitor (appName, { task, taskName, responseTransform = (response) => response }) {
  task = task(appName);
  let taskPromise = task.then ? task : Promise.resolve(task);

  return taskPromise
      .then((task) => runTask(appName, task))
      .then((response) => monitorTask({ taskId: response.ref, appName, taskName, responseTransform }));
}

function orchestrator ({ appName = `test-${Date.now()}`, tasks }) {
  return tasks
    .reduce(
      (sequence, task) => sequence.then(() => runAndMonitor(appName, task)),
      Promise.resolve())
    .catch((error) => console.log('Error:', error));
}

module.exports = orchestrator;
