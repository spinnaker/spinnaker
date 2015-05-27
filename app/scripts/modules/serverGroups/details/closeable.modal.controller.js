'use strict';

angular.module('spinnaker.closable.modal.controller', [])
  .controller('CloseableModalCtrl', function($scope, $modalInstance) {
    $scope.close = $modalInstance.dismiss;
  }
);
