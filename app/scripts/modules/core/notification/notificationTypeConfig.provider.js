'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.type.config', [])
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
