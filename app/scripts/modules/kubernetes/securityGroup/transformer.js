'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.kubernetes.securityGroup.transformer', [
])
  .factory('kubernetesSecurityGroupTransformer', function (settings, $q) {

    function normalizeSecurityGroup(securityGroup) {
      return $q.when(securityGroup); // no-op
    }

    function constructNewSecurityGroupTemplate() {
      return {
        provider: 'kubernetes',
        stack: '',
        detail: '',
        account: settings.providers.kubernetes ? settings.providers.kubernetes.defaults.account : null,
        namespace: settings.providers.kubernetes ? settings.providers.kubernetes.defaults.namespace : null,
        ingress: {
          serviceName: '',
          port: null,
        },

        rules: [],
      };
    }

    function constructNewIngressRule() {
      return {
        host: '',
        value: {
          http: {
            paths: [],
          },
        },
      };
    }

    function constructNewIngressPath() {
      return {
        path: '/',
        ingress: {
          serviceName: '',
          port: null,
        },
      };
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
      constructNewSecurityGroupTemplate: constructNewSecurityGroupTemplate,
      constructNewIngressRule: constructNewIngressRule,
      constructNewIngressPath: constructNewIngressPath,
    };
  });
