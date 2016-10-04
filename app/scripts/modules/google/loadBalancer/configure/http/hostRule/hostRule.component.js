'use strict';

let angular = require('angular');
import {PathRuleTemplate} from '../templates.ts';

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.hostRule.component', [
    require('../pathRule/pathRule.component.js'),
  ])
  .component('gceHostRule', {
    bindings: {
      hostRule: '=',
      index: '=',
      command: '=',
      deleteHostRule: '&'
    },
    templateUrl: require('./hostRule.component.html'),
    controller: function () {
      this.loadBalancer = this.command.loadBalancer;
      let pathRules = this.hostRule.pathMatcher.pathRules;

      this.addPathRule = () => {
        pathRules.push(new PathRuleTemplate());
      };

      this.deletePathRule = (index) => {
        pathRules.splice(index, 1);
      };
    }
  });
