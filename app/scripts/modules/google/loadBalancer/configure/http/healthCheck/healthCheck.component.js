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
      this.allHealthCheckNames = this.command.backingData.healthChecks.map((hc) => hc.name);
      let loadBalancer = this.command.loadBalancer;
      let healthChecksByName = this.backingData.healthChecksKeyedByName;
      let healthChecksByNameCopy = this.backingData.healthChecksKeyedByNameCopy;

      this.onHealthCheckSelect = (selectedHealthCheck) => {
        assign(selectedHealthCheck);
      };

      let getPlain = (healthCheck) => {
        return {
          checkIntervalSec: healthCheck.checkIntervalSec,
          healthyThreshold: healthCheck.healthyThreshold,
          port: healthCheck.port,
          name: healthCheck.name,
          requestPath: healthCheck.requestPath,
          unhealthyThreshold: healthCheck.unhealthyThreshold,
          timeoutSec: healthCheck.timeoutSec,
        };
      };

      this.modified = () => {
        let originalHealthCheck = healthChecksByNameCopy[getHealthCheckName()];
        return originalHealthCheck && !_.isEqual(getPlain(this.healthCheck), getPlain(originalHealthCheck));
      };

      this.revert = () => {
        let originalHealthCheck = _.cloneDeep(healthChecksByNameCopy[getHealthCheckName()]);
        assign(originalHealthCheck);
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
        loadBalancer.healthChecks[this.index] = this.healthCheck = toAssign;
      };

      let getHealthCheckName = () => {
        return _.get(this, 'healthCheck.name');
      };

      if (healthChecksByName[getHealthCheckName()]) {
        this.editExisting = true;
      }
    }
  });
