'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.selector.directive', [
])
  .directive('notificationSelector', function() {
    return {
      restrict: 'E',
      scope: {
        notification: '=',
        level: '='
      },
      templateUrl: require('./notificationSelector.html'),
      controller: 'NotificationSelectorCtrl',
      controllerAs: 'notificationCtrl'
    };
  })
  .controller('NotificationSelectorCtrl', function($scope, notificationTypeService) {

    $scope.notificationTypes = notificationTypeService.listNotificationTypes();

    if (!$scope.notification.type && $scope.notificationTypes && $scope.notificationTypes.length) {
      $scope.notification.type = $scope.notificationTypes[0].key;
    }

    this.clearAddress = function () {
      $scope.notification.address = null;
    };

    this.getNotificationAddressTemplateUrl = function () {
      var notificationConfig = notificationTypeService.getNotificationType($scope.notification.type);
      return notificationConfig ? notificationConfig.addressTemplateUrl : '';
    };

  });
