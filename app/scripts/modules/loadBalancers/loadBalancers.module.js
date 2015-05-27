'use strict';

angular
  .module('spinnaker.loadBalancer', [
    'spinnaker.loadBalancer.controller',
    'spinnaker.loadBalancer.serverGroup',
    'spinnaker.loadBalancer.tag',
    'spinnaker.loadBalancer.aws.details.controller',
    'spinnaker.loadBalancer.gce.details.controller',
    'spinnaker.loadBalancer.aws.create.controller',
    'spinnaker.loadBalancer.gce.create.controller',
    'spinnaker.loadBalancer.nav.controller',
    'spinnaker.loadBalancer.directive',
  ]);
