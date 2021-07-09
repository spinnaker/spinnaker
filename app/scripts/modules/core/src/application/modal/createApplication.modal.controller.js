'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import _ from 'lodash';

import { AccountService } from '../../account/AccountService';
import { CORE_APPLICATION_MODAL_APPLICATIONPROVIDERFIELDS_COMPONENT } from './applicationProviderFields.component';
import { CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT } from '../../chaosMonkey/chaosMonkeyNewApplicationConfig.component';
import { SETTINGS } from '../../config/settings';
import { ApplicationReader } from '../service/ApplicationReader';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { TaskReader } from '../../task/task.read.service';
import { APPLICATION_NAME_VALIDATION_MESSAGES } from './validation/applicationNameValidationMessages.component';
import { VALIDATE_APPLICATION_NAME } from './validation/validateApplicationName.directive';

export const CORE_APPLICATION_MODAL_CREATEAPPLICATION_MODAL_CONTROLLER =
  'spinnaker.application.create.modal.controller';
export const name = CORE_APPLICATION_MODAL_CREATEAPPLICATION_MODAL_CONTROLLER; // for backwards compatibility
module(CORE_APPLICATION_MODAL_CREATEAPPLICATION_MODAL_CONTROLLER, [
  UIROUTER_ANGULARJS,
  APPLICATION_NAME_VALIDATION_MESSAGES,
  VALIDATE_APPLICATION_NAME,
  CORE_APPLICATION_MODAL_APPLICATIONPROVIDERFIELDS_COMPONENT,
  CHAOS_MONKEY_NEW_APPLICATION_CONFIG_COMPONENT,
]).controller('CreateApplicationModalCtrl', [
  '$scope',
  '$q',
  '$log',
  '$state',
  '$uibModalInstance',
  '$timeout',
  function ($scope, $q, $log, $state, $uibModalInstance, $timeout) {
    const applicationLoader = ApplicationReader.listApplications();
    applicationLoader.then((applications) => (this.data.appNameList = _.map(applications, 'name')));

    const providerLoader = AccountService.listProviders();
    providerLoader.then((providers) => (this.data.cloudProviders = providers));

    $q.all([applicationLoader, providerLoader])
      .catch((error) => {
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

    const submitting = () => {
      this.state.errorMessages = [];
      this.state.submitting = true;
    };

    const goIdle = () => {
      this.state.submitting = false;
    };

    let navigateTimeout = null;

    const routeToApplication = () => {
      navigateTimeout = $timeout(() => {
        $state.go('home.applications.application', {
          application: this.application.name,
        });
      }, 1000);
    };

    $scope.$on('$destroy', () => $timeout.cancel(navigateTimeout));

    const waitUntilApplicationIsCreated = (task) => {
      return TaskReader.waitUntilTaskCompletes(task).then(routeToApplication, () => {
        this.state.errorMessages.push('Could not create application: ' + task.failureMessage);
        goIdle();
      });
    };

    const createApplicationFailure = () => {
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

    this.handlePermissionsChange = (permissions) => {
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

    // Enables setting the attributes as a callback in react components
    this.setAttribute = (name, value) => (this.application[name] = value);
  },
]);
