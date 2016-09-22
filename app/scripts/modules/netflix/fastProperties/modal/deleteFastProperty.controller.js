'use strict';

import _ from 'lodash';

let angular = require('angular');

// delete
module.exports = angular
  .module('spinnaker.deleteFastProperty.controller', [
    require('../../../core/account/account.service.js'),
    require('../fastProperty.write.service.js'),
  ])
  .controller('DeleteFastPropertyModalController', function ($uibModalInstance, accountService, fastProperty, fastPropertyWriter) {
    var vm = this;

    vm.fastProperty = fastProperty;

    vm.verification = {};

    vm.cancel = function() {
      $uibModalInstance.dismiss();
    };

    vm.formDisabled = () => !vm.verification.verified || _.isEmpty(vm.fastProperty.cmcTicket);

    vm.confirm = function() {
      fastPropertyWriter.deleteFastProperty(vm.fastProperty).then(function () {
        $uibModalInstance.close();
      });
    };

    return vm;

  });
