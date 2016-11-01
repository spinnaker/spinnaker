'use strict';

let angular = require('angular');
import {HealthCheckTemplate} from '../templates';
import * as _ from 'lodash';

module.exports = angular.module('spinnaker.deck.httpLoadBalancer.healthCheck.component', [
    require('core/cache/cacheInitializer.js'),
    require('core/cache/infrastructureCaches.js'),
  ])
  .component('gceHttpLoadBalancerHealthCheck', {
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
      let healthChecksByName = this.backingData.healthChecksKeyedByName;

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

      let assign = (toAssign) => {
        this.loadBalancer.healthChecks[this.index] = this.healthCheck = toAssign;
      };

      let getHealthCheckName = () => {
        return _.get(this, 'healthCheck.name');
      };

      if (healthChecksByName[getHealthCheckName()]) {
        this.editExisting = true;
      }
    }
  });
