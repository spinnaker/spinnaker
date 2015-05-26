'use strict';

angular.module('spinnaker.notifications')
  .directive('notification', function () {
    return {
      templateUrl: 'scripts/modules/notifications/notification.html',
      scope: {
        notification: '='
      },
      restrict: 'E',
    };
  });
