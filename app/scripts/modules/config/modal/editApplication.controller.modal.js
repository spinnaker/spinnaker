'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.editApplication.modal.controller', [
    require('../../applications/applications.write.service.js'),
    require('utils/lodash.js'),
  ])
  .controller('EditApplicationController', function ($window, $state, application, applicationWriter, _) {
    var vm = this;
    vm.submitting = false;
    vm.errorMsgs = [];
    vm.application = application;
    vm.applicationAttributes = application.attributes;

    function routeToApplication() {
      $state.go(
        'home.applications.application', {
          application: vm.application.name,
        }
      );
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

      applicationWriter.updateApplication(application.attributes)
        .then(
          function(taskResponseList) {
            _.first(taskResponseList)
              .watchForTaskComplete()
              .then(
                routeToApplication,
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

