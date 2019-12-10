'use strict';

const angular = require('angular');
import { PathRuleTemplate } from '../templates';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT =
  'spinnaker.deck.gce.httpLoadBalancer.hostRule.component';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT, [
    require('../pathRule/pathRule.component').name,
  ])
  .component('gceHostRule', {
    bindings: {
      hostRule: '=',
      index: '=',
      command: '=',
      deleteHostRule: '&',
    },
    templateUrl: require('./hostRule.component.html'),
    controller: function() {
      this.loadBalancer = this.command.loadBalancer;
      const pathRules = this.hostRule.pathMatcher.pathRules;

      this.addPathRule = () => {
        pathRules.push(new PathRuleTemplate());
      };

      this.deletePathRule = index => {
        pathRules.splice(index, 1);
      };
    },
  });
