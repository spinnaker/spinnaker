'use strict';

angular
  .module('spinnaker.editNotification.modal.controller',[
  ])
  .controller('EditNotificationController', function ($scope, $modalInstance, notification) {
    var vm = this;
    vm.notification = angular.copy(notification);

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

    } else {
      vm.notification = {};
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
      $modalInstance.close(vm.notification);
    };

    $scope.$watch('selectedWhenOptions', function (a,b) {
      if(a!==b) {
        vm.updateSelectedWhen();
      }
    }, true);

    return vm;
  });
