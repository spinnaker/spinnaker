'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.types.email', [])
  .config(['notificationTypeConfigProvider', function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Email',
      key: 'email',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  }]);
