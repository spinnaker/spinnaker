'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widgets.notification.userNotification', [
  ])
  .component('userNotification', {
    bindings: {
      message: '<',
      dismiss: '&',
      position: '@',
    },
    template: `
      <div class="user-notification" ng-if="$ctrl.message.position === $ctrl.position">
        <div class="message" ng-bind-html="$ctrl.message.body"></div>
        <a class="btn btn-sm btn-link close-notification" href role="button" ng-click="$ctrl.dismiss()">
          <span class="glyphicon glyphicon-remove"></span>
        </a>
      </div>
      `
  });
