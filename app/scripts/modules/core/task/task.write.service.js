'use strict';

import {API_SERVICE} from 'core/api/api.service';
import {TASK_READ_SERVICE} from 'core/task/task.read.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.write.service', [
    API_SERVICE,
    TASK_READ_SERVICE,
  ])
  .factory('taskWriter', function(API, taskReader, $q, $timeout) {

    function getEndpoint(application) {
      return API.all('applications').all(application).all('tasks');
    }

    function postTaskCommand(taskCommand) {
      return getEndpoint(taskCommand.application || taskCommand.project).post(taskCommand);
    }

    function cancelTask(application, taskId) {
      return getEndpoint(application).one(taskId).one('cancel').put().then(() =>
        taskReader.getTask(taskId).then((task) =>
          taskReader.waitUntilTaskMatches(task, (task) => task.status === 'CANCELED')
        )
      );
    }

    function waitUntilTaskIsDeleted(taskId) {
      // wait until the task is gone, i.e. the promise from the get() is rejected, before succeeding
      var deferred = $q.defer();
      API.one('tasks', taskId).get().then(
        () => {
          $timeout(() => {
            // task is still present, try again
            waitUntilTaskIsDeleted(taskId).then(deferred.resolve);
          }, 1000, false);
        },
        (resp) => {
          if (resp.status === 404) {
            // task is no longer present
            deferred.resolve();
          } else {
            $timeout(() => {
              // task is maybe still present, try again
              waitUntilTaskIsDeleted(taskId).then(deferred.resolve);
            }, 1000, false);
          }
        });
      return deferred.promise;
    }

    function deleteTask(taskId) {
      return API.one('tasks', taskId).remove().then(() => waitUntilTaskIsDeleted(taskId));
    }

    return {
      postTaskCommand: postTaskCommand,
      cancelTask: cancelTask,
      deleteTask: deleteTask,
    };

  });
