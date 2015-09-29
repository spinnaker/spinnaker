'use strict';

let angular = require('angular');

require('./editPrecondition.html');

module.exports = angular
  .module('spinnaker.editPrecondition.modal.controller', [
    require('../../utils/lodash.js'),
  ])
  .controller('EditPreconditionController', function ($scope, $modalInstance, precondition, level, _) {
    var vm = this;

    vm.precondition = angular.copy(precondition);
    vm.hasSelectedWhen = false;
    $scope.selectedWhenOptions = {};
    $scope.level = level;

    if(level === 'application' || level === 'pipeline') {
      vm.whenOptions = [
        'pipeline.starting',
        'pipeline.complete',
        'pipeline.failed'
      ];
    } else {
      vm.whenOptions = [
        'stage.starting',
        'stage.complete',
        'stage.failed'
      ];
    }


    vm.updateSelectedWhen = function(){
      var selected = false;
      _.each(vm.whenOptions, function(option){
        if($scope.selectedWhenOptions[option] === true){
          selected = true;
        }
      });
      vm.hasSelectedWhen = selected;
    };

    if(vm.precondition !== undefined) {
      _.each(vm.whenOptions, function (option) {
        if (vm.precondition.when.indexOf(option) > -1) {
          $scope.selectedWhenOptions[option] = true;
        }
      });
      vm.updateSelectedWhen();
    } else {
      vm.precondition = {};
      vm.precondition.level = $scope.level;
      vm.precondition.when = [];
    }

    vm.submit = function() {
      vm.precondition.when = [];
      _.each(vm.whenOptions, function(option){
        if($scope.selectedWhenOptions[option] === true){
          vm.precondition.when.push(option);
        }
      });
      $modalInstance.close(vm.precondition);
    };

    $scope.$watch('selectedWhenOptions', function (a, b) {
      if(a!==b) {
        vm.updateSelectedWhen();
      }
    }, true);

    return vm;
  }).name;
