'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { HealthCheckTemplate } from '../templates';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT =
  'spinnaker.deck.httpLoadBalancer.healthCheck.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT, []).component(
  'gceHttpLoadBalancerHealthCheck',
  {
    bindings: {
      command: '=',
      deleteHealthCheck: '&',
      healthCheck: '=',
      index: '=',
    },
    templateUrl: require('./healthCheck.component.html'),
    controller: function () {
      this.max = Number.MAX_SAFE_INTEGER;
      this.backingData = this.command.backingData;
      this.loadBalancer = this.command.loadBalancer;
      const healthChecksByName = this.backingData.healthChecksKeyedByName;

      this.onHealthCheckSelect = (selectedHealthCheck) => {
        assign(selectedHealthCheck);
      };

      this.getAllHealthCheckNames = () => {
        return this.command.backingData.healthChecks
          .filter((hc) => hc.account === this.loadBalancer.credentials)
          .map((hc) => hc.name);
      };

      this.toggleEditExisting = () => {
        this.editExisting = !this.editExisting;
        if (!this.editExisting) {
          assign(new HealthCheckTemplate());
        } else {
          delete this.healthCheck.name;
        }
      };

      const assign = (toAssign) => {
        this.loadBalancer.healthChecks[this.index] = this.healthCheck = toAssign;
      };

      const getHealthCheckName = () => {
        return _.get(this, 'healthCheck.name');
      };

      this.onProtocolChange = () => {
        if (this.healthCheck.healthCheckType !== this.healthCheckType) {
          assign(Object.assign({}, new HealthCheckTemplate(), { healthCheckType: this.healthCheckType }));
        }
      };

      if (healthChecksByName[getHealthCheckName()]) {
        this.editExisting = true;
      }

      this.healthCheckType = this.healthCheck.healthCheckType;
    },
  },
);
