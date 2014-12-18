'use strict';

angular
  .module('deckApp')
  .controller('CreateApplicationModalCtrl', function($scope, $log, $modalInstance, orcaService) {
    var vm = this;

    vm.applicationsList = $scope.applications;
    vm.application = {};
    vm.errorMsgs = [];
    vm.emailErrorMsg = '';


    (function() {
      vm.appNameList = _.pluck(vm.applicationsList, 'name');
    })();

    vm.clearEmailMsg = function() {
      vm.emailErrorMsg = '';
    };

    vm.submit = function() {

      vm.application.name = vm.application.name.toLowerCase();

      orcaService.createApplication(vm.application)
        .then(function(taskResponse) {
          $log.info('task info', taskResponse);
          taskResponse
            .watchForTaskComplete()
            .then(
              function() {
                $log.debug('success', vm.application);
                $modalInstance.close(vm.application);
              },
              extractErrorMsg
            );
        }, function(error) {
          $log.debug('create app error', error);
        });
    };

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
