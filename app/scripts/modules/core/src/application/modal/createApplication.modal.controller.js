'use strict';

import _ from 'lodash';
import { AccountService } from 'core/account/AccountService';
import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { ApplicationWriter } from 'core/application/service/ApplicationWriter';
import { APPLICATION_NAME_VALIDATION_MESSAGES } from './validation/applicationNameValidationMessages.component';
import { TaskReader } from 'core/task/task.read.service';
import { VALIDATE_APPLICATION_NAME } from './validation/validateApplicationName.directive';
import { CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT } from 'core/chaosMonkey/chaosMonkeyNewApplicationConfig.component';
import { SETTINGS } from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.application.create.modal.controller', [
    require('@uirouter/angularjs').default,
    APPLICATION_NAME_VALIDATION_MESSAGES,
    VALIDATE_APPLICATION_NAME,
    require('./applicationProviderFields.component').name,
    CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT,
  ])
  .controller('CreateApplicationModalCtrl', function($scope, $q, $log, $state, $uibModalInstance, $timeout) {
    let applicationLoader = ApplicationReader.listApplications();
    applicationLoader.then(applications => (this.data.appNameList = _.map(applications, 'name')));

    let providerLoader = AccountService.listProviders();
    providerLoader.then(providers => (this.data.cloudProviders = providers));

    $q.all([applicationLoader, providerLoader])
      .catch(error => {
        this.state.initializeFailed = true;
        throw error;
      })
      .finally(() => (this.state.initializing = false));

    this.state = {
      initializing: true,
      initializeFailed: false,
      submitting: false,
      errorMessages: [],
      permissionsInvalid: false,
    };
    this.data = {
      gitSources: SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab'],
    };
    this.application = {
      cloudProviders: [],
      instancePort: SETTINGS.defaultInstancePort || null,
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
        $state.go('home.applications.application.insight.clusters', {
          application: this.application.name,
        });
      }, 1000);
    };

    $scope.$on('$destroy', () => $timeout.cancel(navigateTimeout));

    let waitUntilApplicationIsCreated = task => {
      return TaskReader.waitUntilTaskCompletes(task).then(routeToApplication, () => {
        this.state.errorMessages.push('Could not create application: ' + task.failureMessage);
        goIdle();
      });
    };

    let createApplicationFailure = () => {
      this.state.errorMessages.push('Could not create application');
      goIdle();
    };

    this.createApplicationForTests = () => {
      return ApplicationWriter.createApplication(this.application).then(
        waitUntilApplicationIsCreated,
        createApplicationFailure,
      );
    };

    this.updateCloudProviderHealthWarning = () => {
      if (!this.application.platformHealthOnlyShowOverride) {
        // Show the warning if platformHealthOnlyShowOverride is being enabled.
        this.data.showOverrideWarning = `Simply enabling the "Consider only cloud provider health when executing tasks"
          option above is usually sufficient for most applications that want the same health provider behavior for
          all stages. Note that pipelines will require manual updating if this setting is disabled in the future.`;
      }
    };

    function permissionsAreValid(permissions) {
      if (permissions.READ.includes(null) || permissions.WRITE.includes(null)) {
        return false;
      }
      if (permissions.READ.length > 0 && permissions.WRITE.length === 0) {
        return false;
      }
      return true;
    }

    this.handlePermissionsChange = permissions => {
      this.state.permissionsInvalid = !permissionsAreValid(permissions);
      this.application.permissions = permissions;
      $scope.$digest();
    };

    this.submit = () => {
      submitting();
      this.application.name = this.application.name.toLowerCase();
      if (this.data.cloudProviders.length === 1) {
        this.application.cloudProviders = this.data.cloudProviders;
      }
      this.createApplicationForTests();
    };
  });
