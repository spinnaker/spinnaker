'use strict';

const angular = require('angular');
import { PathRuleTemplate } from '../templates';

module.exports = angular
  .module('spinnaker.deck.gce.httpLoadBalancer.hostRule.component', [require('../pathRule/pathRule.component.js').name])
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
