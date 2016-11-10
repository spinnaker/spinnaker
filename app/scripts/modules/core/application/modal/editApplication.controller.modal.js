'use strict';

import _ from 'lodash';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.editApplication.modal.controller', [
    require('../service/applications.write.service.js'),
    ACCOUNT_SERVICE,
    require('../../task/task.read.service.js'),
    require('./applicationProviderFields.component.js'),
  ])
  .controller('EditApplicationController', function ($window, $state, $uibModalInstance, application, applicationWriter,
                                                     accountService, taskReader) {
    var vm = this;
    this.data = {};
    this.state = {
      submitting: false,
    };
    vm.errorMsgs = [];
    vm.application = application;
    vm.applicationAttributes = _.cloneDeep(application.attributes);
    vm.applicationAttributes.cloudProviders = application.attributes.cloudProviders ?
      application.attributes.cloudProviders.split(',') :
      [];

    accountService.listProviders().then((providers) => vm.data.cloudProviders = providers);

    function closeModal() {
      vm.data.cloudProviders = null; // wha? prevents a fight with the ui-select directive trying to invalidate the selections
      vm.applicationAttributes.cloudProviders = vm.applicationAttributes.cloudProviders.join(',');
      $uibModalInstance.close(vm.applicationAttributes);
    }

    function extractErrorMsg(error) {
      var exceptions = _.chain(error.variables)
        .filter({key: 'exception'})
        .head()
        .value()
        .value
        .details
        .errors;

      angular.copy(exceptions, vm.errorMsgs );
      assignErrorMsgs();
      goIdle();
    }

    function assignErrorMsgs() {
      vm.emailErrorMsg = vm.errorMsgs.filter(function(msg) {
        return msg
            .toLowerCase()
            .includes('email');
      });
    }

    function goIdle() {
      vm.state.submitting = false;
    }

    function submitting() {
      vm.state.submitting = true;
    }

    vm.updateCloudProviderHealthWarning = (platformHealthOnlyShowOverrideClicked) => {
      if (vm.applicationAttributes.platformHealthOnlyShowOverride
          && (platformHealthOnlyShowOverrideClicked || vm.applicationAttributes.platformHealthOnly)) {
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

    vm.clearEmailMsg = function() {
      vm.state.emailErrorMsg = '';
    };

    vm.submit = function () {
      submitting();

      applicationWriter.updateApplication(vm.applicationAttributes)
        .then(
          (task) => taskReader.waitUntilTaskCompletes(application.name, task).then(closeModal, extractErrorMsg),
          () => vm.errorMsgs.push('Could not update application')
        );
    };

    return vm;
  });

