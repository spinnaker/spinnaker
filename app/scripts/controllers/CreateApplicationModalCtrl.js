'use strict';

angular
  .module('deckApp')
  .controller('CreateApplicationModalCtrl', function($scope, $log, $modalInstance, orcaService) {
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

      orcaService.createApplication(vm.application)
        .then(function(taskResponse) {
          taskResponse
            .watchForTaskComplete()
            .then(
              function() {
                $modalInstance.close(vm.application);
              },
              extractErrorMsg
            )
            .then(goIdle);
        }, function() {
          vm.errorMsgs.push('Could not create application');
        })
        .then(goIdle);
    };

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

    }

    return vm;
  });
