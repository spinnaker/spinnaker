'use strict';

angular.module('deckApp')
  .factory('confirmationModalService', function($modal) {
    var defaults = {
      buttonText: 'Confirm'
    };

    function confirm(params) {
      params = angular.extend(angular.copy(defaults), params);

      var controller = function($scope, $modalInstance) {
        $scope.params = params;

        $scope.confirm = function () {
          $modalInstance.close(true);
        };

        $scope.cancel = function () {
          $modalInstance.dismiss();
        };
      };

      var modalArgs = {
        templateUrl: 'views/modal/confirm.html',
        controller: controller
      };

      if (params.size) {
        modalArgs.size = params.size;
      }
      return $modal.open(modalArgs).result;
    }

    return {
      confirm: confirm
    };
  });
