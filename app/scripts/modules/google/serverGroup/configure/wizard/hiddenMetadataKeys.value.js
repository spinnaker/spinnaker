'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.serverGroup.hiddenMetadataKeys.value',[])
  .value('gceServerGroupHiddenMetadataKeys', [
    'load-balancer-names',
    'global-load-balancer-names',
    'backend-service-names',
    'load-balancing-policy'
  ]);
