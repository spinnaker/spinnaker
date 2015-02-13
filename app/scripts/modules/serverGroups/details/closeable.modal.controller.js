'use strict';

angular.module('deckApp.closable.modal.controller', [])
  .controller('CloseableModalCtrl', function($scope, $modalInstance) {
    $scope.close = $modalInstance.dismiss;
  }
);
