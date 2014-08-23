'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ConfirmationModalCtrl', function ($scope, $modalInstance, params) {
    $scope.params = params;

    this.confirm = function () {
      $modalInstance.close(true);
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  }
);
