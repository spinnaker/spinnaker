'use strict';

angular.module('spinnaker.notifications.config', [])
  .provider('notificationTypeConfig', function() {

    var notificationTypes = [];

    function registerNotificationType(config) {
      notificationTypes.push(config);
    }

    function listNotificationTypes() {
      return angular.copy(notificationTypes);
    }

    this.registerNotificationType = registerNotificationType;

    this.$get = function() {
      return {
        listNotificationTypes: listNotificationTypes
      };
    };

  }
);
