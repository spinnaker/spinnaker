'use strict';

const angular = require('angular');

require('./notifier.component.less');

module.exports = angular
  .module('spinnaker.core.widgets.notification', [
    require('./notifier.service'),
  ])
  .component('notifier', {
    templateUrl: require('./notifier.component.html'),
    controller: function(notifierService) {
      this.messages = [];
      notifierService.messageStream.subscribe((message) => {
        this.messages.push(message);
      });

      this.dismiss = (index) => {
        this.messages.splice(index, 1);
      };
    }
  });
