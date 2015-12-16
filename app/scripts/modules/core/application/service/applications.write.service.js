'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.write.service', [
    require('../../task/taskExecutor.js'),
    require('../../utils/lodash.js'),
  ])
  .factory('applicationWriter', function($q, taskExecutor, _) {

    function createApplication(app, account) {
      var command = _.cloneDeep(app);
      command.cloudProviders = command.cloudProviders.join(',');
      delete command.account;
      return taskExecutor.executeTask({
        job: [
          {
            type: 'createApplication',
            account: account,
            application: command,
          }
        ],
        application: app,
        description: 'Create Application: ' + app.name
      });
    }

    function updateApplication (app) {
      var taskList = [];
      var accounts = app.accounts.split(',');
      var command = _.cloneDeep(app);
      if (command.cloudProviders) {
        command.cloudProviders = command.cloudProviders.join(',');
      }
      delete command.account;

      accounts.forEach(function(account) {
        taskList.push(taskExecutor.executeTask({
          job: [
            {
              type: 'updateApplication',
              account: account,
              application: command,
            }
          ],
          application: app,
          description: 'Updating Application: ' + app.name
        }));
      });

      return $q.all(taskList);

    }


    function deleteApplication(app) {
      var taskList = [];
      var accounts = app.accounts && app.accounts.length ? app.accounts.split(',') : [];

      accounts.forEach(function(account) {
        taskList.push(
          {
            job: [
              {
                type: 'deleteApplication',
                account: account,
                application: {
                  name: app.name,
                }
              }
            ],
            application: app,
            description: 'Deleting Application: ' + app.name
          }
        );
      });

      return executeDeleteTasks(taskList);

    }

    function executeDeleteTasks(taskList, deferred) {
      if(!deferred) {
        deferred = $q.defer();
      }

      if(taskList.length > 1) {
        taskExecutor.executeTask(_(taskList).head())
          .then(function(taskResponse) {
            taskResponse.watchForTaskComplete()
              .then( function() {
                executeDeleteTasks(_(taskList).tail(), deferred);
              })
              .catch(function(err) {
                console.warn(err);
              });
          });
      } else {
        deferred.resolve(taskExecutor.executeTask(_(taskList).head()));
      }

      return deferred.promise;
    }

    return {
      createApplication: createApplication,
      updateApplication: updateApplication,
      deleteApplication: deleteApplication
    };

  });
