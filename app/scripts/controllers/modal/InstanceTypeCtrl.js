'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('InstanceTypeCtrl', function($scope, instanceTypeService) {

    $scope.instanceTypeCtrl = this;

    instanceTypeService.getCategories().then(function(categories) {
      categories.forEach(function(profile) {
        if (profile.type === $scope.command.instanceProfile) {
          $scope.selectedInstanceProfile = profile;
        }
      });
    });

    this.selectInstanceType = function(type) {
      $scope.command.instanceType = type;
    };

  });
