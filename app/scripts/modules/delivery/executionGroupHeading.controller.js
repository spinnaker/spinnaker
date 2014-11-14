'use strict';

angular.module('deckApp.delivery')
  .controller('executionGroupHeading', function($scope) {
    var controller = this;

    $scope.open = true;

    controller.toggle = function() {
      $scope.open = !$scope.open;
    };
  });

