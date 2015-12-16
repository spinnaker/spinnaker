'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.manualJudgment.modal.editNotification', [])
  .controller('ManualJudgmentEditNotificationController', function ($scope, $modalInstance, notification) {

    var vm = this;
    $scope.notification = angular.copy(notification);

    vm.submit = function() {
      $modalInstance.close($scope.notification);
    };

    return vm;
  });
