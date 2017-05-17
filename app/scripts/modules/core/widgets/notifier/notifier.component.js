'use strict';

const angular = require('angular');

import './notifier.component.less';

module.exports = angular
  .module('spinnaker.core.widgets.notification', [
    require('./notifier.service'),
    require('./userNotification.component'),
  ])
  .component('notifier', {
    templateUrl: require('./notifier.component.html'),
    controller: function(notifierService) {
      this.messages = [];
      notifierService.messageStream.subscribe((message) => {
        if (message.action === 'remove') {
          this.messages = this.messages.filter(m => m.key !== message.key);
        } else {
          if (this.messages.some(m => m.key === message.key)) {
            this.messages.filter(m => m.key === message.key).forEach(m => m.body = message.body);
          } else {
            this.messages.push(message);
          }
        }
      });

      this.dismiss = (index) => {
        this.messages.splice(index, 1);
      };
    }
  });
