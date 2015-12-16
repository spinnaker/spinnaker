'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.write.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('./task.read.service.js'),
  ])
  .factory('taskWriter', function(Restangular, taskReader, $q, $timeout) {

    var endpoint = Restangular.all('applications');

    function getEndpoint(application) {
      return endpoint.all(application).all('tasks');
    }

    function postTaskCommand(taskCommand) {
      return getEndpoint(taskCommand.application).post(taskCommand);
    }

    function cancelTask(application, taskId) {
      return getEndpoint(application).one(taskId).one('cancel').put().then(() =>
        waitUntilTaskMatches(application, taskId, (task) => task.status === 'CANCELED')
      );
    }

    function waitUntilTaskMatches(application, taskId, closure) {
      return taskReader.getOneTaskForApplication(application, taskId).then((task) => {
        if (closure(task)) {
          return task;
        }
        return $timeout(() => waitUntilTaskMatches(application, taskId, closure), 1000);
      });
    }

    function waitUntilTaskIsDeleted(taskId) {
      // wait until the task is gone, i.e. the promise from the get() is rejected, before succeeding
      var deferred = $q.defer();
      Restangular.one('tasks', taskId).get().then(
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
      return Restangular.one('tasks', taskId).remove().then(() => waitUntilTaskIsDeleted(taskId));
    }

    return {
      postTaskCommand: postTaskCommand,
      cancelTask: cancelTask,
      deleteTask: deleteTask,
    };

  });
