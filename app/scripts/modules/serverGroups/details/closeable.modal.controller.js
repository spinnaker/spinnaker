'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.closable.modal.controller', [])
  .controller('CloseableModalCtrl', function($scope, $modalInstance) {
    $scope.close = $modalInstance.dismiss;
  }
).name;
