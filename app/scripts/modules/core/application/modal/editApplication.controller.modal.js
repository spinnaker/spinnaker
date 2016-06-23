'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.editApplication.modal.controller', [
    require('../service/applications.write.service.js'),
    require('../../utils/lodash.js'),
    require('../../account/account.service.js'),
    require('../../task/task.read.service.js'),
    require('./applicationProviderFields.component.js'),
    require('../../config/settings'),
  ])
  .controller('EditApplicationController', function ($window, $state, $uibModalInstance, application, applicationWriter,
                                                     _, accountService, taskReader, settings) {
    var vm = this;
    this.data = {};
    this.state = {
      submitting: false,
    };
    vm.errorMsgs = [];
    vm.application = application;
    vm.applicationAttributes = _.cloneDeep(application.attributes);
    vm.applicationAttributes.instancePort = vm.applicationAttributes.instancePort || settings.defaultInstancePort || null;
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
        .where({key: 'exception'})
        .first()
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
            .indexOf('email') > -1;
      });
    }

    function goIdle() {
      vm.state.submitting = false;
    }

    function submitting() {
      vm.state.submitting = true;
    }

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

