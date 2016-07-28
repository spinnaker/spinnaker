'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serialize.write.service', [
    require('../task/taskExecutor.js'),
    require('../utils/lodash.js'),
    require('../account/account.service.js')
  ])
  .factory('serializeWriter', function($q, taskExecutor, accountService, _) {

    let serializationProviders = ['gce'];

    function buildJobs(app, accountDetails) {
      let jobs = [];
      accountDetails.forEach((accountDetail) => {
        if (serializationProviders.includes(accountDetail.cloudProvider)) {
          jobs.push({
            type: 'serializeApplication',
            credentials: accountDetail.name,
            applicationName: app.name,
            cloudProvider: accountDetail.cloudProvider,
          });
        }
      });
      return jobs;
    }

    function loadAccountDetails(app) {
      let accounts = _.isString(app.accounts) ? app.accounts.split(',') : [];
      let accountDetailPromises = accounts.map(account => accountService.getAccountDetails(account));
      return $q.all(accountDetailPromises);
    }

    function serializeApplication(app) {
      return loadAccountDetails(app).then(function(accountDetails) {
        let jobs = buildJobs(app, accountDetails);
        return taskExecutor.executeTask({
          job: jobs,
          application: app,
          description: 'Serialize Application: ' + app.name
        });
      });
    }

    return {
      serializeApplication: serializeApplication,
    };

  });
