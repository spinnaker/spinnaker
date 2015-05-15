'use strict';

// delete
angular
  .module('deckApp.deleteFastProperty.controller', [
    'deckApp.account.service',
    'deckApp.fastProperty.write.service',
  ])
  .controller('DeleteFastPropertyModalController', function ($modalInstance, accountService, fastProperty, fastPropertyWriter) {
    var vm = this;

    vm.fastProperty = fastProperty;

    vm.verification = {
      requireAccountEntry: accountService.challengeDestructiveActions('aws', vm.fastProperty.env),
      verifyAccount: ''
    };

    vm.cancel = function() {
      $modalInstance.dismiss();
    };

    vm.formDisabled = function () {
      return (vm.verification.requireAccountEntry  && vm.verification.verifyAccount !== vm.fastProperty.env.toUpperCase()) || _.isEmpty(vm.fastProperty.cmcTicket);
    };

    vm.confirm = function() {
      fastPropertyWriter.deleteFastProperty(vm.fastProperty).then(function () {
        $modalInstance.close();
      });
    };

    return vm;

  });
