'use strict';

let angular = require('angular');

// delete
module.exports = angular
  .module('spinnaker.deleteFastProperty.controller', [
    require('../../../core/account/account.service.js'),
    require('../fastProperty.write.service.js'),
    require('../../../core/utils/lodash.js'),
  ])
  .controller('DeleteFastPropertyModalController', function ($modalInstance, accountService, fastProperty, fastPropertyWriter, _) {
    var vm = this;

    vm.fastProperty = fastProperty;

    vm.verification = {
      requireAccountEntry: false,
      verifyAccount: ''
    };

    accountService.challengeDestructiveActions(vm.fastProperty.env).then((challenge) => {
      vm.verification.requireAccountEntry = challenge;
    });

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
