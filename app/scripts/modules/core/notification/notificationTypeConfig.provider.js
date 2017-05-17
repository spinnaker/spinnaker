'use strict';

import {SETTINGS} from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.type.config', [])
  .provider('notificationTypeConfig', function() {

    var notificationTypes = [];

    function registerNotificationType(config) {
      config.config = SETTINGS.notifications && SETTINGS.notifications[config.key] || { enabled: true };
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
