'use strict';

import * as angular from 'angular';
import _ from 'lodash';

import { AccountService } from '../../account/AccountService';
import { CORE_APPLICATION_MODAL_APPLICATIONPROVIDERFIELDS_COMPONENT } from './applicationProviderFields.component';
import { SETTINGS } from '../../config/settings';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { TaskReader } from '../../task/task.read.service';

export const CORE_APPLICATION_MODAL_EDITAPPLICATION_CONTROLLER_MODAL = 'spinnaker.editApplication.modal.controller';
export const name = CORE_APPLICATION_MODAL_EDITAPPLICATION_CONTROLLER_MODAL; // for backwards compatibility
angular
  .module(CORE_APPLICATION_MODAL_EDITAPPLICATION_CONTROLLER_MODAL, [
    CORE_APPLICATION_MODAL_APPLICATIONPROVIDERFIELDS_COMPONENT,
  ])
  .controller('EditApplicationController', [
    '$scope',
    '$window',
    '$state',
    '$uibModalInstance',
    'application',
    function ($scope, $window, $state, $uibModalInstance, application) {
      const vm = this;
      this.data = {
        gitSources: SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab'],
      };
      this.state = {
        submitting: false,
        permissionsInvalid: false,
      };
      vm.errorMsgs = [];
      vm.application = application;
      vm.applicationAttributes = _.cloneDeep(application.attributes);

      AccountService.listProviders().then((providers) => (vm.data.cloudProviders = providers));

      function closeModal() {
        $uibModalInstance.close(vm.applicationAttributes);
      }

      function extractErrorMsg(error) {
        const exceptions = _.chain(error.variables).filter({ key: 'exception' }).head().value().value.details.errors;

        angular.copy(exceptions, vm.errorMsgs);
        assignErrorMsgs();
        goIdle();
      }

      function assignErrorMsgs() {
        vm.emailErrorMsg = vm.errorMsgs.filter(function (msg) {
          return msg.toLowerCase().includes('email');
        });
      }

      function goIdle() {
        vm.state.submitting = false;
      }

      function submitting() {
        vm.state.submitting = true;
      }

      vm.updateCloudProviderHealthWarning = (platformHealthOnlyShowOverrideClicked) => {
        if (
          vm.applicationAttributes.platformHealthOnlyShowOverride &&
          (platformHealthOnlyShowOverrideClicked || vm.applicationAttributes.platformHealthOnly)
        ) {
          // Show the warning if platformHealthOnlyShowOverride is being disabled, or if both options are enabled and
          // platformHealthOnly is being disabled.
          vm.data.showOverrideWarning = `Note that disabling this setting will not have an effect on any
          pipeline stages with the "Consider only 'platform' health?" option explicitly enabled. You will
          need to update each of those pipeline stages individually if desired.`;
        } else if (!vm.applicationAttributes.platformHealthOnlyShowOverride && platformHealthOnlyShowOverrideClicked) {
          // Show the warning if platformHealthOnlyShowOverride is being enabled.
          vm.data.showOverrideWarning = `Simply enabling the "Consider only cloud provider health when executing tasks"
          option above is usually sufficient for most applications that want the same health provider behavior for
          all stages. Note that pipelines will require manual updating if this setting is disabled in the future.`;
        }
      };

      vm.clearEmailMsg = function () {
        vm.state.emailErrorMsg = '';
      };

      // Allow for setting the attributes through this as a callback in react components
      vm.setAttribute = function (name, value) {
        vm.applicationAttributes[name] = value;
      };

      vm.submit = function () {
        submitting();

        if (vm.applicationAttributes.aliases === '') {
          delete vm.applicationAttributes.aliases;
        }
        if (vm.applicationAttributes.aliases) {
          vm.applicationAttributes.aliases = vm.applicationAttributes.aliases
            .split(/\s*,\s*/)
            .filter((s) => s !== '')
            .join(',');
        }

        ApplicationWriter.updateApplication(vm.applicationAttributes).then(
          (task) => TaskReader.waitUntilTaskCompletes(task).then(closeModal, extractErrorMsg),
          () => vm.errorMsgs.push('Could not update application'),
        );
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

      vm.handlePermissionsChange = (permissions) => {
        vm.state.permissionsInvalid = !permissionsAreValid(permissions);
        vm.applicationAttributes.permissions = permissions;
        delete vm.applicationAttributes.requiredGroupMembership;
        $scope.$digest();
      };

      return vm;
    },
  ]);
