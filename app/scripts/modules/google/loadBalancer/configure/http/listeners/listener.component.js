'use strict';

import * as _ from 'lodash';
let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.listener.component', [])
  .component('gceListener', {
    bindings: {
      command: '=',
      listener: '=',
      deleteListener: '&',
      index: '=',
      application: '=',
    },
    templateUrl: require('./listener.component.html'),
    controller: function () {
      this.certificates = this.command.backingData.certificates;
      this.originalListener = _.cloneDeep(this.listener);
      let loadBalancerMap = this.command.backingData.loadBalancerMap;

      this.preventPortChange = () => {
        return this.listener.created &&
          this.originalListener.name === this.listener.name &&
          (this.originalListener.port === 443 || this.originalListener.port === '443');
      };

      this.getName = (listener, applicationName) => {
        let listenerName = [applicationName, (listener.stack || ''), (listener.detail || '')].join('-');
        return _.trimEnd(listenerName, '-');
      };

      this.updateName = (listener, appName) => {
        listener.name = this.getName(listener, appName);
      };

      this.localListenerHasSameName = () => {
        return this.command.loadBalancer.listeners.filter(listener => listener.name === this.listener.name).length > 1;
      };

      this.existingListenerNames = () => {
        return loadBalancerMap[this.command.loadBalancer.credentials].listeners;
      };

      this.isHttps = (port) => port === 443 || port === '443';

      if (!this.listener.name) {
        this.updateName(this.listener, this.application.name);
      }
    }
  });
