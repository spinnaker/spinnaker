'use strict';

import { module } from 'angular';

import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_PATHRULE_PATHRULE_COMPONENT } from '../pathRule/pathRule.component';
import { PathRuleTemplate } from '../templates';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT =
  'spinnaker.deck.gce.httpLoadBalancer.hostRule.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT, [
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_PATHRULE_PATHRULE_COMPONENT,
]).component('gceHostRule', {
  bindings: {
    hostRule: '=',
    index: '=',
    command: '=',
    deleteHostRule: '&',
  },
  templateUrl: require('./hostRule.component.html'),
  controller: function () {
    this.loadBalancer = this.command.loadBalancer;
    const pathRules = this.hostRule.pathMatcher.pathRules;

    this.addPathRule = () => {
      pathRules.push(new PathRuleTemplate());
    };

    this.deletePathRule = (index) => {
      pathRules.splice(index, 1);
    };
  },
});
