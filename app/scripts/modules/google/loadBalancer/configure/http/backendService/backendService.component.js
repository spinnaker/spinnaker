'use strict';

let angular = require('angular');
import * as _ from 'lodash';
import {BackendServiceTemplate} from '../templates.ts';

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.backendService.component', [])
  .component('gceBackendService', {
    bindings: {
      deleteService: '&',
      backendService: '=',
      command: '=',
      index: '='
    },
    templateUrl: require('./backendService.component.html'),
    controller: function () {
      this.backingData = this.command.backingData;
      this.allServiceNames = this.command.backingData.backendServices.map((service) => service.name);
      let loadBalancer = this.command.loadBalancer;
      let servicesByName = this.backingData.backendServicesKeyedByName;
      let servicesByNameCopy = this.backingData.backendServicesKeyedByNameCopy;

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
        let allHealthChecks = loadBalancer.healthChecks.concat(this.backingData.healthChecks);
        return _.uniq(allHealthChecks.map((hc) => hc.name));
      };

      this.maxCookieTtl = 60 * 60 * 24; // One day.

      let getPlain = (service) => {
        return {
          healthCheck: service.healthCheck,
          name: service.name,
          sessionAffinity: service.sessionAffinity,
          affinityCookieTtlSec: Number(service.affinityCookieTtlSec), // Can be string or number.
        };
      };

      this.modified = () => {
        let originalService = servicesByNameCopy[this.backendService.name];
        return originalService && !_.isEqual(getPlain(this.backendService), getPlain(originalService));
      };

      this.revert = () => {
        let originalService = _.cloneDeep(servicesByNameCopy[this.backendService.name]);
        assign(originalService);
        this.command.onHealthCheckSelected(originalService.healthCheck, this.command);
      };

      if (servicesByName[this.backendService.name]) {
        this.editExisting = true;
      }

      let assign = (toAssign) => {
        loadBalancer.backendServices[this.index] = this.backendService = toAssign;
      };
    }
  });
