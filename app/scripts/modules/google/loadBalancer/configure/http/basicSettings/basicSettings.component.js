'use strict';

import * as _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.basicSettings.component', [])
  .component('gceHttpLoadBalancerBasicSettings', {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./basicSettings.component.html'),
    controller: function () {
      let c = this.command;
      this.loadBalancer = c.loadBalancer;
      this.accounts = c.backingData.accounts;
      let loadBalancerMap = c.backingData.loadBalancerMap;

      this.getName = (loadBalancer, applicationName) => {
        let loadBalancerName = [applicationName, (loadBalancer.stack || ''), (loadBalancer.detail || '')].join('-');
        return _.trimEnd(loadBalancerName, '-');
      };

      this.updateName = (lb, appName) => {
        lb.urlMapName = this.getName(lb, appName);
      };

      this.accountChanged = (account) => {
        this.existingLoadBalancerNames = _.get(loadBalancerMap, [account, 'urlMapNames']) || [];
        c.onAccountChange(c);
      };

      if (!this.loadBalancer.name) {
        this.updateName(this.loadBalancer, this.application.name);
      }

      this.existingLoadBalancerNames = _.get(loadBalancerMap, [this.loadBalancer.credentials, 'urlMapNames']) || [];
    }
  });
