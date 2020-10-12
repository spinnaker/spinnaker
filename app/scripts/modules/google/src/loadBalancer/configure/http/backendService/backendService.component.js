'use strict';

import { module } from 'angular';
import _ from 'lodash';
import { BackendServiceTemplate } from '../templates';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT =
  'spinnaker.deck.gce.httpLoadBalancer.backendService.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT, []).component(
  'gceHttpLoadBalancerBackendServiceSelector',
  {
    bindings: {
      deleteService: '&',
      backendService: '=',
      command: '=',
      index: '=',
    },
    templateUrl: require('./backendService.component.html'),
    controller: function () {
      this.backingData = this.command.backingData;
      this.loadBalancer = this.command.loadBalancer;
      const servicesByName = this.backingData.backendServicesKeyedByName;

      this.onBackendServiceSelect = (selectedBackendService) => {
        assign(selectedBackendService);
        this.command.onHealthCheckSelected(selectedBackendService.healthCheck, this.command);
      };

      this.toggleEditExisting = () => {
        this.editExisting = !this.editExisting;
        if (!this.editExisting) {
          const template = new BackendServiceTemplate();
          assign(template);
        } else {
          delete this.backendService.name;
        }
      };

      this.getAllHealthChecks = () => {
        const allHealthChecks = this.loadBalancer.healthChecks.concat(this.backingData.healthChecks);
        return _.chain(allHealthChecks)
          .filter((hc) => hc.account === this.loadBalancer.credentials || !hc.account)
          .map((hc) => hc.name)
          .uniq()
          .value();
      };

      this.getSessionAffinitySuggestions = () => {
        if (this.loadBalancer.loadBalancerType === 'HTTP') {
          return ['None', 'Client IP', 'Generated Cookie'];
        } else {
          return ['None', 'Client IP', 'Generated Cookie', 'Header Field', 'HTTP Cookie'];
        }
      };

      this.getAllServiceNames = () => {
        return this.command.backingData.backendServices
          .filter((service) => service.account === this.loadBalancer.credentials)
          .map((service) => service.name);
      };

      this.maxCookieTtl = 60 * 60 * 24; // One day.

      const getBackendServiceName = () => {
        return _.get(this, 'backendService.name');
      };

      if (servicesByName[getBackendServiceName()]) {
        this.editExisting = true;
      }

      const assign = (toAssign) => {
        this.loadBalancer.backendServices[this.index] = this.backendService = toAssign;
      };
    },
  },
);
