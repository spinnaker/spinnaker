'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.pathRule.component', [])
  .component('gcePathRule', {
    bindings: {
      pathRule: '=',
      backendServices: '=',
      index: '=',
      deletePathRule: '&'
    },
    templateUrl: require('./pathRule.component.html'),
    controller: function () {
      this.isNameDefined = (backendService) => angular.isDefined(backendService.name);

      this.oneBackendServiceIsConfigured = () => {
        return this.backendServices.filter(this.isNameDefined).length > 0;
      };
    }
  });
