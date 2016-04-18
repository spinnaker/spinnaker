'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.type.config', [
    require('../config/settings')
  ])
  .provider('notificationTypeConfig', function(settings) {

    var notificationTypes = [];

    function registerNotificationType(config) {
      config.config = settings.notifications[config.key] || { enabled: true };
      notificationTypes.push(config);
    }

    function listNotificationTypes() {
      return angular.copy(notificationTypes).filter(n => n.config.enabled);
    }

    this.registerNotificationType = registerNotificationType;

    this.$get = function() {
      return {
        listNotificationTypes: listNotificationTypes
      };
    };

  }
);
