'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.closable.controller', [])
  .controller('CloseableModalCtrl', function($scope, $modalInstance) {
    $scope.close = $modalInstance.dismiss;
  }
);
