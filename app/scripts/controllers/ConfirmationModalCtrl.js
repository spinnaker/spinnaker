'use strict';

module.exports = function($scope, $modalInstance, params) {
  $scope.params = params;

  $scope.confirm = function () {
    $modalInstance.close(true);
  };

  $scope.cancel = function () {
    $modalInstance.dismiss();
  };
};
