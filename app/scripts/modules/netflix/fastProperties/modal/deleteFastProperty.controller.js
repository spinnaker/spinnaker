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

    vm.verification = {};

    vm.cancel = function() {
      $modalInstance.dismiss();
    };

    vm.formDisabled = () => vm.verification.verified || _.isEmpty(vm.fastProperty.cmcTicket);

    vm.confirm = function() {
      fastPropertyWriter.deleteFastProperty(vm.fastProperty).then(function () {
        $modalInstance.close();
      });
    };

    return vm;

  });
