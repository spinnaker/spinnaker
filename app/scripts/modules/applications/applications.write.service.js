'use strict';

angular
  .module('deckApp.applications.write.service', [])
  .factory('applicationWriter', function(taskExecutor) {

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

    return {
      createApplication: createApplication
    };

  });
