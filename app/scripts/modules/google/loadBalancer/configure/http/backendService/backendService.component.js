'use strict';

let angular = require('angular');
require('./backendService.component.less');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.backendService.component', [])
  .component('gceBackendService', {
    bindings: {
      backendService: '=',
      deleteService: '&',
      healthChecks: '=',
      index: '=',
      defaultServiceManager: '&'
    },
    templateUrl: require('./backendService.component.html'),
    controller: function () {
      this.isNameDefined = (healthCheck) => angular.isDefined(healthCheck.name);

      this.oneHealthCheckIsConfigured = () => {
        return this.healthChecks.filter(this.isNameDefined).length > 0;
      };
    }
  });
