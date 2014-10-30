'use strict';

angular.module('deckApp')
  .controller('CloseableModalCtrl', function($scope, $modalInstance) {
    $scope.close = $modalInstance.dismiss;
  }
);
