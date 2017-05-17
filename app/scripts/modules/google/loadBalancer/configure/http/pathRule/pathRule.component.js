'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.pathRule.component', [])
  .component('gcePathRule', {
    bindings: {
      pathRule: '=',
      command: '=',
      index: '=',
      deletePathRule: '&'
    },
    templateUrl: require('./pathRule.component.html')
  });
