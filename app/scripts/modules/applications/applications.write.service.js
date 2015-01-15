'use strict';

angular
  .module('deckApp.applications.write.service', [
    'deckApp.taskExecutor.service'
  ])
  .factory('applicationWriter', function($q, taskExecutor) {

    function createApplication(app) {
      return taskExecutor.executeTask({
        suppressNotification: true,
        job: [
          {
            type: 'createApplication',
            account: app.account,
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
              tags: app.tags
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
                tags: app.tags
              }
            }
          ],
          application: app,
          description: 'Updating Application: ' + app.name
        }));
      });

      return $q.all(taskList);

    }

    return {
      createApplication: createApplication,
      updateApplication: updateApplication
    };

  });
