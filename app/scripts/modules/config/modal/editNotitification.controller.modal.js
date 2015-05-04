'use strict';

angular
  .module('deckApp.editNotification.modal.controller',[
  ])
  .controller('EditNotificationController', function ($modalInstance, notification) {
    var vm = this;
    vm.notification = angular.copy(notification);

    vm.types = [
      'email', 'hipchat', 'sms'
    ];

    vm.selectedWhenOptions = {};

    vm.whenOptions = [
      'pipeline.started',
      'pipeline.complete',
      'pipeline.failed'
    ];

    if(vm.notification !== undefined) {
      _.each(vm.whenOptions, function (option) {
        if (vm.notification.when.indexOf(option) > -1) {
          vm.selectedWhenOptions[option] = true;
        }
      });
    } else {
      vm.notification = [];
      vm.notification.address = '';
      vm.notification.type = 'email';
      vm.notification.level = 'application';
    }

    vm.submit = function() {
      vm.notification.when = [];
      $modalInstance.close([notification, vm.notification]);
    };

    return vm;
  });
