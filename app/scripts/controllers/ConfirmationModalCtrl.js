'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ConfirmationModalCtrl', function ($scope, $modalInstance, params) {
    $scope.params = params;

    $scope.confirm = function () {
      $modalInstance.close(true);
    };

    $scope.cancel = function () {
      $modalInstance.dismiss();
    };
  }
);
