'use strict';

angular
  .module('deckApp.editNotification.modal.controller',[
  ])
  .controller('EditNotificationController', function ($scope, $modalInstance, notification) {
    var vm = this;
    vm.notification = angular.copy(notification);

    vm.types = [
      'email', 'hipchat'
    ];

    vm.hasSelectedWhen = false;
    $scope.selectedWhenOptions = {};

    vm.whenOptions = [
      'pipeline.starting',
      'pipeline.complete',
      'pipeline.failed'
    ];


    vm.updateSelectedWhen = function(){
      var selected = false;
      _.each(vm.whenOptions, function(option){
        if($scope.selectedWhenOptions[option] === true){
          selected = true;
        }
      });
      vm.hasSelectedWhen = selected;
    };

    if(vm.notification !== undefined) {
      _.each(vm.whenOptions, function (option) {
        if (vm.notification.when.indexOf(option) > -1) {
          $scope.selectedWhenOptions[option] = true;
        }
      });
      vm.updateSelectedWhen();

      if(vm.notification.type ==='email'){
        vm.email = vm.notification.address;
      } else {
        vm.hipchat = vm.notification.address;
      }

    } else {
      vm.notification = {};
      vm.notification.address = '';
      vm.notification.type = 'email';
      vm.notification.level = 'application';
      vm.notification.when = [];
    }

    vm.submit = function() {
      vm.notification.when = [];
      _.each(vm.whenOptions, function(option){
        if($scope.selectedWhenOptions[option] === true){
          vm.notification.when.push(option);
        }
      });
      if(vm.notification.type ==='email'){
        vm.notification.address = vm.email;
      } else {
        vm.notification.address = vm.hipchat;
      }
      $modalInstance.close([notification, vm.notification]);
    };

    $scope.$watch('selectedWhenOptions', function (a,b) {
      if(a!==b) {
        vm.updateSelectedWhen();
      }
    }, true);

    return vm;
  });
