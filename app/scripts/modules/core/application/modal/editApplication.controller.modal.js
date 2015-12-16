'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.editApplication.modal.controller', [
    require('../service/applications.write.service.js'),
    require('../../utils/lodash.js'),
    require('../../account/account.service.js')
  ])
  .controller('EditApplicationController', function ($window, $state, $modalInstance, application, applicationWriter,
                                                     _, accountService) {
    var vm = this;
    vm.submitting = false;
    vm.errorMsgs = [];
    vm.application = application;
    vm.applicationAttributes = _.cloneDeep(application.attributes);
    vm.applicationAttributes.cloudProviders = application.attributes.cloudProviders ?
      application.attributes.cloudProviders.split(',') :
      [];

    accountService.listProviders().then((providers) => vm.cloudProviders = providers);

    function closeModal() {
      vm.cloudProviders = null; // wha? prevents a fight with the ui-select directive trying to invalidate the selections
      vm.applicationAttributes.cloudProviders = vm.applicationAttributes.cloudProviders.join(',');
      $modalInstance.close(vm.applicationAttributes);
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
      vm.emailErrorMsg = vm.errorMsgs.filter(function(msg){
        return msg
            .toLowerCase()
            .indexOf('email') > -1;
      });
    }

    function goIdle() {
      vm.submitting = false;
    }

    function submitting() {
      vm.submitting = true;
    }

    vm.clearEmailMsg = function() {
      vm.emailErrorMsg = '';
    };

    vm.submit = function () {
      submitting();

      applicationWriter.updateApplication(vm.applicationAttributes)
        .then(
          function(taskResponseList) {
            _.first(taskResponseList)
              .watchForTaskComplete()
              .then(
                closeModal,
                extractErrorMsg
              );
          },
          function() {
            vm.errorMsgs.push('Could not update application');
          }
        );
    };

    return vm;
  });

