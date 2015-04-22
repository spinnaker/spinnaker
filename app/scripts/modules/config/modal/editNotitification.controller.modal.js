'use strict';

angular
  .module('deckApp.editNotification.modal.controller',[
  ])
  .controller('EditNotificationController', function (notification) {
    var vm = this;
    vm.notification = notification;

    vm.types = [
      'email', 'hipchat', 'sms'
    ];

    vm.whenOptions = [
      'task.starting',
      'task.complete',
      'task.failed',
      'pipeline.starting',
      'pipeline.complete',
      'pipeline.failed'
    ];

    return vm;
  });

