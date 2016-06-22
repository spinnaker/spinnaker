'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.write.service', [
    require('../../task/taskExecutor.js'),
    require('../../utils/lodash.js'),
    require('../../history/recentHistory.service.js'),
  ])
  .factory('applicationWriter', function($q, taskExecutor, recentHistoryService, _) {

    function buildJobs(app, accounts, type, commandTransformer) {
      let jobs = [];
      var command = commandTransformer(app);
      if (command.cloudProviders) {
        command.cloudProviders = command.cloudProviders.join(',');
      }
      delete command.account;
      accounts.forEach((account) => {
        jobs.push({
          type: type,
          account: account,
          application: command
        });
      });
      return jobs;
    }

    function createApplication(app) {
      let jobs = buildJobs(app, app.account, 'createApplication', _.cloneDeep);
      return taskExecutor.executeTask({
        job: jobs,
        application: app,
        description: 'Create Application: ' + app.name
      });
    }

    function updateApplication (app) {
      let accounts = app.accounts && app.accounts.length ? app.accounts.split(',') : [];
      let jobs = buildJobs(app, accounts, 'updateApplication', _.cloneDeep);
      return taskExecutor.executeTask({
        job: jobs,
        application: app,
        description: 'Update Application: ' + app.name
      });
    }


    function deleteApplication(app) {
      let accounts = app.accounts && app.accounts.length ? app.accounts.split(',') : [];
      let jobs = buildJobs(app, accounts, 'deleteApplication', (app) => { return { name: app.name }; });
      return taskExecutor.executeTask({
        job: jobs,
        application: app,
        description: 'Deleting Application: ' + app.name
      })
      .then((task) => {
        recentHistoryService.removeByAppName(app.name);
        return task;
      })
      .catch((task) => task);
    }

    function pageApplicationOwner(app, reason) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'pageApplicationOwner',
            application: app.name,
            message: reason
          }
        ],
        application: app,
        description: 'Paged Application Owner'
      });
    }

    return {
      createApplication: createApplication,
      updateApplication: updateApplication,
      deleteApplication: deleteApplication,
      pageApplicationOwner: pageApplicationOwner,
    };

  });
