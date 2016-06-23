'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.application.create.modal.controller', [
    require('angular-ui-router'),
    require('../service/applications.write.service.js'),
    require('../service/applications.read.service.js'),
    require('../../utils/lodash.js'),
    require('../../account/account.service.js'),
    require('../../task/task.read.service.js'),
    require('../../config/settings'),
    require('./validation/applicationNameValidationMessages.directive.js'),
    require('./validation/validateApplicationName.directive.js'),
    require('./applicationProviderFields.component.js'),
  ])
  .controller('CreateApplicationModalCtrl', function($scope, $q, $log, $state, $uibModalInstance, accountService,
                                                     applicationWriter, applicationReader, _, taskReader, $timeout,
                                                     settings) {

    let applicationLoader = applicationReader.listApplications();
    applicationLoader.then((applications) => this.data.appNameList = _.pluck(applications, 'name'));

    let accountLoader = accountService.listAccounts();
    accountLoader.then((accounts) => this.data.accounts = accounts);

    let providerLoader = accountService.listProviders();
    providerLoader.then((providers) => this.data.cloudProviders = providers);

    $q.all([accountLoader, applicationLoader, providerLoader]).then(() => this.state.initializing = false);

    this.state = {
      initializing: true,
      submitting: false,
      errorMessages: [],
    };
    this.data = {

    };
    this.application = {
      cloudProviders: [],
      instancePort: settings.defaultInstancePort || null,
    };

    let submitting = () => {
      this.state.errorMessages = [];
      this.state.submitting = true;
    };

    let goIdle = () => {
      this.state.submitting = false;
    };

    var navigateTimeout = null;

    let routeToApplication = () => {
      navigateTimeout = $timeout(() => {
        $state.go(
          'home.applications.application.insight.clusters', {
            application: this.application.name,
          }
        );
      }, 1000 );
    };

    $scope.$on('$destroy', () => $timeout.cancel(navigateTimeout));


    let waitUntilApplicationIsCreated = (task) => {
      return taskReader.waitUntilTaskCompletes(this.application.name, task)
        .then(routeToApplication, () => {
          this.state.errorMessages.push('Could not create application: ' + task.failureMessage);
          goIdle();
        });
    };

    let createApplicationFailure = () => {
      this.state.errorMessages.push('Could not create application');
      goIdle();
    };

    this.createApplication = () => {
      return applicationWriter.createApplication(this.application)
        .then(waitUntilApplicationIsCreated, createApplicationFailure);
    };

    this.submit = () => {
      submitting();
      this.application.name = this.application.name.toLowerCase();
      if (this.data.cloudProviders.length === 1) {
        this.application.cloudProviders = this.data.cloudProviders;
      }
      this.createApplication();

    };

  });
