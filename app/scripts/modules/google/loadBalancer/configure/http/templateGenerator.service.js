'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.httpLoadBalancer.templateGenerator.service', [
    require('../../../../core/config/settings.js')
  ])
  .factory('gceHttpLoadBalancerTemplateGenerator', function (settings) {
    function httpLoadBalancerTemplate() {
      return {
        provider: 'gce',
        stack: '',
        detail: '',
        credentials: _.get(settings, 'providers.gce.defaults.account') || null,
        region: 'global',
        loadBalancerType: 'HTTP',
        portRange: '8080',
        certificate: '',
        defaultService: {},
        hostRules: [],
      };
    }

    function backendServiceTemplate () {
      return {
        backends: [],
        useAsDefault: false
      };
    }

    function healthCheckTemplate () {
      return {
        requestPath: '/',
        port: 80,
        checkIntervalSec: 10,
        timeoutSec: 5,
        healthyThreshold: 10,
        unhealthyThreshold: 2
      };
    }

    function hostRuleTemplate () {
      return {
        hostPatterns: [],
        pathMatcher: {
          pathRules: []
        }
      };
    }

    function pathRuleTemplate () {
      return {
        paths: []
      };
    }

    return {
      backendServiceTemplate,
      healthCheckTemplate,
      hostRuleTemplate,
      pathRuleTemplate,
      httpLoadBalancerTemplate
    };
  });
