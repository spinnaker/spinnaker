'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.closable.controller', [])
  .controller('CloseableModalCtrl', function($scope, $uibModalInstance) {
    $scope.close = $uibModalInstance.dismiss;
  }
);
