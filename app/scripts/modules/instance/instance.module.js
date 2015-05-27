'use strict';

angular
  .module('spinnaker.instance', [
    'spinnaker.instance.detail.aws.controller',
    'spinnaker.instance.detail.gce.controller',
    'spinnaker.instance.loadBalancer.health.directive',
  ]);
