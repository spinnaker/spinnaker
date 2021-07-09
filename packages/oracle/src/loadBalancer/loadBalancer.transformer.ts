import { module } from 'angular';
import { $q } from 'ngimport';

import { Application } from '@spinnaker/core';
import {
  IOracleBackEndSet,
  IOracleListener,
  IOracleListenerCertificate,
  IOracleListenerSSLConfiguration,
  IOracleLoadBalancer,
  IOracleLoadBalancerUpsertCommand,
  LoadBalancingPolicy,
} from '../domain/IOracleLoadBalancer';
import { OracleProviderSettings } from '../oracle.settings';
import { OracleDefaultProviderSettings } from '../oracle.settings';

export class OracleLoadBalancerTransformer {
  public normalizeLoadBalancer(loadBalancer: IOracleLoadBalancer): PromiseLike<IOracleLoadBalancer> {
    /*loadBalancer.serverGroups.forEach(function(serverGroup) {
      serverGroup.account = loadBalancer.account;
      serverGroup.region = loadBalancer.region;

      if (serverGroup.detachedInstances) {
        serverGroup.detachedInstances = serverGroup.detachedInstances.map(function(instanceId) {
          return { id: instanceId };
        });
        serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
      } else {
        serverGroup.detachedInstances = [];
      }
    });
    var activeServerGroups = _.filter(loadBalancer.serverGroups, { isDisabled: false });
    loadBalancer.provider = loadBalancer.type;
    loadBalancer.instances = _.chain(activeServerGroups)
      .map('instances')
      .flatten()
      .value();
    loadBalancer.detachedInstances = _.chain(activeServerGroups)
      .map('detachedInstances')
      .flatten()
      .value();*/
    return $q.resolve(loadBalancer);
  }

  public convertLoadBalancerForEditing(loadBalancer: IOracleLoadBalancer): IOracleLoadBalancerUpsertCommand {
    if (loadBalancer.listeners) {
      Object.keys(loadBalancer.listeners).forEach((key) => {
        const lis = loadBalancer.listeners[key];
        lis.isSsl = !!lis.sslConfiguration; // use !! operator to get truthiness value
      });
    }
    const toEdit: IOracleLoadBalancerUpsertCommand = {
      name: loadBalancer.name,
      cloudProvider: loadBalancer.cloudProvider,
      credentials: loadBalancer.account,
      region: loadBalancer.region,
      shape: loadBalancer.shape,
      isPrivate: loadBalancer.isPrivate,
      subnetIds: loadBalancer.subnets.map((subnet) => subnet.id),
      certificates: loadBalancer.certificates,
      listeners: loadBalancer.listeners,
      hostnames: loadBalancer.hostnames,
      backendSets: loadBalancer.backendSets,
      freeformTags: loadBalancer.freeformTags,
      loadBalancerType: loadBalancer.type,
      securityGroups: loadBalancer.securityGroups,
      vpcId: loadBalancer.vpcId,
      subnetTypeMap: loadBalancer.subnetTypeMap,
    };
    return toEdit;
  }

  public constructNewLoadBalancerTemplate(application: Application): IOracleLoadBalancerUpsertCommand {
    const defaultCredentials =
      application.defaultCredentials.oracle ||
      (OracleProviderSettings.defaults
        ? OracleProviderSettings.defaults.account
        : OracleDefaultProviderSettings.defaults.account);
    const defaultRegion =
      application.defaultRegions.oracle ||
      (OracleProviderSettings.defaults
        ? OracleProviderSettings.defaults.region
        : OracleDefaultProviderSettings.defaults.region);
    return {
      name: undefined,
      cloudProvider: 'oracle',
      credentials: defaultCredentials,
      region: defaultRegion,
      shape: null,
      isPrivate: false,
      subnetIds: [],
      listeners: {},
      hostnames: [],
      backendSets: {},
      freeformTags: {},
      loadBalancerType: null,
      securityGroups: [],
      vpcId: null,
      subnetTypeMap: {},
    };
  }

  public constructNewListenerTemplate(): IOracleListener {
    return {
      name: 'HTTP_80',
      port: 80,
      protocol: 'HTTP',
      defaultBackendSetName: undefined,
      isSsl: false,
    };
  }

  public constructNewBackendSetTemplate(name: string): IOracleBackEndSet {
    return {
      name: name,
      policy: LoadBalancingPolicy.ROUND_ROBIN,
      healthChecker: { protocol: 'HTTP', port: 80, urlPath: '/' },
      backends: [],
      isNew: true,
    };
  }

  public constructNewSSLConfiguration(): IOracleListenerSSLConfiguration {
    return {
      certificateName: '',
      verifyDepth: 0,
      verifyPeerCertificates: false,
    };
  }

  public constructNewCertificateTemplate(name: string): IOracleListenerCertificate {
    return {
      certificateName: name,
      publicCertificate: undefined,
      caCertificate: undefined,
      privateKey: undefined,
      passphrase: undefined,
      isNew: true,
    };
  }
}

export const ORACLE_LOAD_BALANCER_TRANSFORMER = 'spinnaker.oracle.loadBalancer.transformer';
module(ORACLE_LOAD_BALANCER_TRANSFORMER, []).service('oracleLoadBalancerTransformer', OracleLoadBalancerTransformer);
