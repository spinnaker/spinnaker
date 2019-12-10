'use strict';

import { module } from 'angular';

import { KubernetesProviderSettings } from '../../kubernetes.settings';

export const KUBERNETES_V1_SECURITYGROUP_TRANSFORMER = 'spinnaker.kubernetes.securityGroup.transformer';
export const name = KUBERNETES_V1_SECURITYGROUP_TRANSFORMER; // for backwards compatibility
module(KUBERNETES_V1_SECURITYGROUP_TRANSFORMER, []).factory('kubernetesSecurityGroupTransformer', [
  '$q',
  function($q) {
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
        tls: [],
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

    function constructNewIngressTLS() {
      return {
        hosts: [],
        secretName: '',
      };
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
      constructNewSecurityGroupTemplate: constructNewSecurityGroupTemplate,
      constructNewIngressRule: constructNewIngressRule,
      constructNewIngressPath: constructNewIngressPath,
      constructNewIngressTLS: constructNewIngressTLS,
    };
  },
]);
