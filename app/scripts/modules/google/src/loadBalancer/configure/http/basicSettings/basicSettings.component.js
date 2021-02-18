'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT =
  'spinnaker.deck.gce.httpLoadBalancer.basicSettings.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT, []).component(
  'gceHttpLoadBalancerBasicSettings',
  {
    bindings: {
      command: '=',
      application: '=',
    },
    templateUrl: require('./basicSettings.component.html'),
    controller: function () {
      const c = this.command;
      this.loadBalancer = c.loadBalancer;
      this.accounts = c.backingData.accounts;
      const loadBalancerMap = c.backingData.loadBalancerMap;

      this.getName = (loadBalancer, applicationName) => {
        const loadBalancerName = [applicationName, loadBalancer.stack || '', loadBalancer.detail || ''].join('-');
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
    },
  },
);
