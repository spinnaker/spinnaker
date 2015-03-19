'use strict';

angular
  .module('deckApp.application.create.modal.controller', [
    'ui.router',
    'ui.bootstrap',
    'deckApp.applications.write.service',
  ])
  .controller('CreateApplicationModalCtrl', function($scope, $log, $state, $modalInstance, applicationWriter) {
    var vm = this;

    vm.appNameList = _.pluck($scope.applications, 'name');
    vm.submitting = false;
    vm.application = {};
    vm.errorMsgs = [];
    vm.emailErrorMsg = [];

    vm.clearEmailMsg = function() {
      vm.emailErrorMsg = '';
    };

    vm.submit = function() {

      submitting();

      vm.application.name = vm.application.name.toLowerCase();

      applicationWriter.createApplication(vm.application)
        .then(function(taskResponse) {
          taskResponse
            .watchForTaskComplete()
            .then(
              routeToApplication,
              extractErrorMsg
            );
        }, function() {
          vm.errorMsgs.push('Could not create application');
          goIdle();
        });
    };

    function routeToApplication() {
      $state.go(
        'home.applications.application.insight.clusters', {
          application: vm.application.name,
        }
      );
    }

    function submitting() {
      vm.submitting = true;
    }

    function goIdle() {
      vm.submitting = false;
    }

    function assignErrorMsgs() {
      vm.emailErrorMsg = vm.errorMsgs.filter(function(msg){
        return msg
          .toLowerCase()
          .indexOf('email') > -1;
      });
    }


    function extractErrorMsg(error) {
      $log.debug('extract error', error);
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

    return vm;
  });
