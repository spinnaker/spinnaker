'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.selector.directive', [])
  .directive('notificationSelector', function() {
    return {
      restrict: 'E',
      bindToController: {
        notification: '=',
        level: '=',
      },
      scope: {},
      templateUrl: require('./notificationSelector.html'),
      controller: 'NotificationSelectorCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('NotificationSelectorCtrl', [
    'notificationTypeService',
    function(notificationTypeService) {
      this.notificationTypes = notificationTypeService.listNotificationTypes();

      this.originalType = this.notification.type;
      this.originalAddress = this.notification.address;

      this.updateNotificationType = function() {
        if (this.notification.type === this.originalType) {
          this.notification.address = this.originalAddress;
        } else {
          this.notification.address = null;
        }

        let notificationConfig = notificationTypeService.getNotificationType(this.notification.type);
        this.notificationConfig = notificationConfig ? notificationConfig.config : {};
        this.addressTemplateUrl = notificationConfig ? notificationConfig.addressTemplateUrl : '';
      };

      if (!this.notification.type && this.notificationTypes && this.notificationTypes.length) {
        this.notification.type = this.notificationTypes[0].key;
        this.notificationConfig = this.notificationTypes[0].config;
      }

      this.updateNotificationType();
    },
  ]);
