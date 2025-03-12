'use strict';

import { module } from 'angular';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_PATHRULE_PATHRULE_COMPONENT =
  'spinnaker.deck.gce.httpLoadBalancer.pathRule.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_PATHRULE_PATHRULE_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_PATHRULE_PATHRULE_COMPONENT, []).component('gcePathRule', {
  bindings: {
    pathRule: '=',
    command: '=',
    index: '=',
    deletePathRule: '&',
  },
  templateUrl: require('./pathRule.component.html'),
});
