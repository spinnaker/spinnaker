'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.write.service', [
    require('../../task/taskExecutor.js'),
    require('../../utils/lodash.js'),
  ])
  .factory('applicationWriter', function($q, taskExecutor, _) {

    function createApplication(app, account) {
      return taskExecutor.executeTask({
        suppressNotification: true,
        job: [
          {
            type: 'createApplication',
            account: account,
            application: {
              name: app.name,
              description: app.description,
              email: app.email,
              owner: app.owner,
              type: app.type,
              group: app.group,
              monitorBucketType: app.monitorBucketType,
              pdApiKey: app.pdApiKey,
              updateTs: app.updateTs,
              createTs: app.createTs,
              tags: app.tags,
              repoProjectKey: app.repoProjectKey,
              repoSlug: app.repoSlug,
              repoType: app.repoType,
              cloudProviders: app.cloudProviders.join(','),
              platformHealthOnly: app.platformHealthOnly,
              platformHealthOnlyShowOverride: app.platformHealthOnlyShowOverride,
            }
          }
        ],
        application: app,
        description: 'Create Application: ' + app.name
      });
    }

    function updateApplication (app) {
      var taskList = [];
      var accounts = app.accounts.split(',');

      accounts.forEach(function(account) {
        taskList.push(taskExecutor.executeTask({
          suppressNotification: true,
          job: [
            {
              type: 'updateApplication',
              account: account,
              application: {
                name: app.name,
                description: app.description,
                email: app.email,
                owner: app.owner,
                type: app.type,
                group: app.group,
                monitorBucketType: app.monitorBucketType,
                pdApiKey: app.pdApiKey,
                updateTs: app.updateTs,
                createTs: app.createTs,
                tags: app.tags,
                repoProjectKey: app.repoProjectKey,
                repoSlug: app.repoSlug,
                repoType: app.repoType,
                cloudProviders: app.cloudProviders.join(','),
                platformHealthOnly: app.platformHealthOnly,
                platformHealthOnlyShowOverride: app.platformHealthOnlyShowOverride,
              }
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
            suppressNotification: true,
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

  }).name;
