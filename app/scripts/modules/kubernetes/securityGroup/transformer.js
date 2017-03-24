'use strict';

let angular = require('angular');

import {KubernetesProviderSettings} from '../kubernetes.settings';

module.exports = angular.module('spinnaker.kubernetes.securityGroup.transformer', [
])
  .factory('kubernetesSecurityGroupTransformer', function ($q) {

    function normalizeSecurityGroup(securityGroup) {
      return $q.when(securityGroup); // no-op
    }

    function constructNewSecurityGroupTemplate() {
      return {
        provider: 'kubernetes',
        stack: '',
        detail: '',
        account: KubernetesProviderSettings.defaults.account,
        namespace: KubernetesProviderSettings.defaults.namespace,
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
