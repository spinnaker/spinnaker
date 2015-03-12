'use strict';

angular
  .module('deckApp.instance', [
    'deckApp.instance.detail.aws.controller',
    'deckApp.instance.detail.gce.controller',
    'deckApp.instance.loadBalancer.health.directive',
  ]);
