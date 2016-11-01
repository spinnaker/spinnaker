'use strict';

let angular = require('angular');
import * as _ from 'lodash';
import {BackendServiceTemplate} from '../templates';

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.backendService.component', [])
  .component('gceHttpLoadBalancerBackendServiceSelector', {
    bindings: {
      deleteService: '&',
      backendService: '=',
      command: '=',
      index: '='
    },
    templateUrl: require('./backendService.component.html'),
    controller: function () {
      this.backingData = this.command.backingData;
      this.loadBalancer = this.command.loadBalancer;
      let servicesByName = this.backingData.backendServicesKeyedByName;

      this.onBackendServiceSelect = (selectedBackendService) => {
        assign(selectedBackendService);
        this.command.onHealthCheckSelected(selectedBackendService.healthCheck, this.command);
      };

      this.toggleEditExisting = () => {
        this.editExisting = !this.editExisting;
        if (!this.editExisting) {
          let template = new BackendServiceTemplate();
          assign(template);
        } else {
          delete this.backendService.name;
        }
      };

      this.getAllHealthChecks = () => {
        let allHealthChecks = this.loadBalancer.healthChecks.concat(this.backingData.healthChecks);
        return _.chain(allHealthChecks)
          .filter((hc) => hc.account === this.loadBalancer.credentials || !hc.account)
          .map((hc) => hc.name)
          .uniq()
          .value();
      };

      this.getAllServiceNames = () => {
        return this.command.backingData.backendServices
          .filter((service) => service.account === this.loadBalancer.credentials)
          .map((service) => service.name);
      };

      this.maxCookieTtl = 60 * 60 * 24; // One day.

      let getBackendServiceName = () => {
        return _.get(this, 'backendService.name');
      };

      if (servicesByName[getBackendServiceName()]) {
        this.editExisting = true;
      }

      let assign = (toAssign) => {
        this.loadBalancer.backendServices[this.index] = this.backendService = toAssign;
      };
    }
  });
