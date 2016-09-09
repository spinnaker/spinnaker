'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.hostRule.component', [
    require('../pathRule/pathRule.component.js'),
    require('../templateGenerator.service.js'),
  ])
  .component('gceHostRule', {
    bindings: {
      hostRule: '=',
      index: '=',
      backendServices: '=',
      deleteHostRule: '&'
    },
    templateUrl: require('./hostRule.component.html'),
    controller: function (gceHttpLoadBalancerTemplateGenerator) {
      let { pathRuleTemplate } = gceHttpLoadBalancerTemplateGenerator,
        pathRules = this.hostRule.pathMatcher.pathRules;

      if (!pathRules.length) {
        pathRules.push(pathRuleTemplate());
      }

      this.isNameDefined = (backendService) => angular.isDefined(backendService.name);

      this.oneBackendServiceIsConfigured = () => {
        return this.backendServices.filter(this.isNameDefined).length > 0;
      };

      this.addPathRule = () => {
        pathRules.push(pathRuleTemplate());
      };

      this.deletePathRule = (index) => {
        pathRules.splice(index, 1);
      };
    }
  });
