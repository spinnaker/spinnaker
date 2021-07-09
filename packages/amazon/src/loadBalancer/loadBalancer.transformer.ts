import { chain, filter, flatten, map } from 'lodash';
import { $q } from 'ngimport';

import {
  AccountService,
  Application,
  IHealth,
  IInstance,
  IServerGroup,
  IVpc,
  NameUtils,
  SETTINGS,
} from '@spinnaker/core';
import { AWSProviderSettings } from '../aws.settings';
import {
  IALBListenerCertificate,
  IAmazonApplicationLoadBalancer,
  IAmazonApplicationLoadBalancerUpsertCommand,
  IAmazonClassicLoadBalancer,
  IAmazonClassicLoadBalancerUpsertCommand,
  IAmazonLoadBalancer,
  IAmazonNetworkLoadBalancerUpsertCommand,
  IAmazonServerGroup,
  IApplicationLoadBalancerSourceData,
  IClassicListenerDescription,
  IClassicLoadBalancerSourceData,
  INetworkLoadBalancerSourceData,
  ITargetGroup,
} from '../domain';
import { VpcReader } from '../vpc/VpcReader';

export class AwsLoadBalancerTransformer {
  private updateHealthCounts(container: IServerGroup | ITargetGroup | IAmazonLoadBalancer): void {
    const instances = container.instances;

    container.instanceCounts = {
      up: instances.filter((instance) => instance.health[0].state === 'InService').length,
      down: instances.filter((instance) => instance.healthState === 'Down').length,
      outOfService: instances.filter((instance) => instance.healthState === 'OutOfService').length,
      starting: undefined,
      succeeded: undefined,
      failed: undefined,
      unknown: undefined,
    };

    if ((container as ITargetGroup | IAmazonLoadBalancer).serverGroups) {
      const serverGroupInstances = flatten((container as ITargetGroup).serverGroups.map((sg) => sg.instances));
      container.instanceCounts.up = serverGroupInstances.filter(
        (instance) => instance.health[0].state === 'InService',
      ).length;
      container.instanceCounts.down = serverGroupInstances.filter((instance) => instance.healthState === 'Down').length;
      container.instanceCounts.outOfService = serverGroupInstances.filter(
        (instance) => instance.healthState === 'OutOfService',
      ).length;
    }
  }

  private transformInstance(instance: IInstance, provider: string, account: string, region: string): void {
    // instance in this case should be some form if instance source data, but force to 'any' type to fix
    // instnace health in load balancers until we can actually shape this bit properly
    const health: IHealth = (instance.health as any) || ({} as IHealth);
    if (health.state === 'healthy') {
      // Target groups use 'healthy' instead of 'InService' and a lot of deck expects InService
      // to surface health in the UI; just set it as InService since we don't really care the
      // specific state name... yet
      health.state = 'InService';
    }
    instance.provider = provider;
    instance.account = account;
    instance.region = region;
    instance.healthState = health.state ? (health.state === 'InService' ? 'Up' : 'Down') : 'OutOfService';
    instance.health = [health];
  }

  private addVpcNameToContainer(
    container: IAmazonLoadBalancer | ITargetGroup,
  ): (vpcs: IVpc[]) => IAmazonLoadBalancer | ITargetGroup {
    return (vpcs: IVpc[]) => {
      const match = vpcs.find((test) => test.id === container.vpcId);
      container.vpcName = match ? match.name : '';
      return container;
    };
  }

  private normalizeServerGroups(
    serverGroups: IServerGroup[],
    container: IAmazonLoadBalancer | ITargetGroup,
    containerType: string,
    healthType: string,
  ): void {
    serverGroups.forEach((serverGroup) => {
      serverGroup.account = serverGroup.account || container.account;
      serverGroup.region = serverGroup.region || container.region;
      serverGroup.cloudProvider = serverGroup.cloudProvider || container.cloudProvider;

      if (serverGroup.detachedInstances) {
        serverGroup.detachedInstances = (serverGroup.detachedInstances as any).map((instanceId: string) => {
          return { id: instanceId } as IInstance;
        });
        serverGroup.instances = serverGroup.instances.concat(serverGroup.detachedInstances);
      } else {
        serverGroup.detachedInstances = [];
      }

      serverGroup.instances.forEach((instance) => {
        this.transformInstance(instance, container.type, container.account, container.region);
        (instance as any)[containerType] = [container.name];
        (instance.health as any).type = healthType;
      });
      this.updateHealthCounts(serverGroup);
    });
  }

  private normalizeTargetGroup(targetGroup: ITargetGroup): PromiseLike<ITargetGroup> {
    this.normalizeServerGroups(targetGroup.serverGroups, targetGroup, 'targetGroups', 'TargetGroup');

    const activeServerGroups = filter(targetGroup.serverGroups, { isDisabled: false });
    targetGroup.provider = targetGroup.type;
    targetGroup.instances = chain(activeServerGroups).map('instances').flatten<IInstance>().value();
    targetGroup.detachedInstances = chain(activeServerGroups).map('detachedInstances').flatten<IInstance>().value();
    this.updateHealthCounts(targetGroup);

    return $q.all([VpcReader.listVpcs(), AccountService.listAllAccounts()]).then(([vpcs, accounts]) => {
      const tg = this.addVpcNameToContainer(targetGroup)(vpcs) as ITargetGroup;

      tg.serverGroups = tg.serverGroups.map((serverGroup) => {
        const account = accounts.find((x) => x.name === serverGroup.account);
        const cloudProvider = (account && account.cloudProvider) || serverGroup.cloudProvider;

        serverGroup.cloudProvider = cloudProvider;
        serverGroup.instances.forEach((instance) => {
          instance.cloudProvider = cloudProvider;
          instance.provider = cloudProvider;
        });

        return { ...serverGroup, cloudProvider };
      });

      return tg;
    });
  }

  private normalizeActions(loadBalancer: IAmazonApplicationLoadBalancer) {
    if (loadBalancer.loadBalancerType === 'application') {
      const alb = loadBalancer as IAmazonApplicationLoadBalancer;

      // Sort the actions by the order specified since amazon does not return them in order of order
      alb.listeners.forEach((l) => {
        l.defaultActions.sort((a, b) => a.order - b.order);
        l.rules.forEach((r) => r.actions.sort((a, b) => a.order - b.order));
      });
    }
  }

  public normalizeLoadBalancer(loadBalancer: IAmazonLoadBalancer): PromiseLike<IAmazonLoadBalancer> {
    this.normalizeServerGroups(loadBalancer.serverGroups, loadBalancer, 'loadBalancers', 'LoadBalancer');

    let serverGroups = loadBalancer.serverGroups;
    if ((loadBalancer as IAmazonApplicationLoadBalancer).targetGroups) {
      const appLoadBalancer = loadBalancer as IAmazonApplicationLoadBalancer;
      appLoadBalancer.targetGroups.forEach((targetGroup) => this.normalizeTargetGroup(targetGroup));
      serverGroups = flatten<IAmazonServerGroup>(map(appLoadBalancer.targetGroups, 'serverGroups'));
    }

    loadBalancer.loadBalancerType = loadBalancer.loadBalancerType || 'classic';
    loadBalancer.provider = loadBalancer.type;

    this.normalizeActions(loadBalancer as IAmazonApplicationLoadBalancer);

    const activeServerGroups = filter(serverGroups, { isDisabled: false });
    loadBalancer.instances = chain(activeServerGroups).map('instances').flatten<IInstance>().value();
    loadBalancer.detachedInstances = chain(activeServerGroups).map('detachedInstances').flatten<IInstance>().value();
    this.updateHealthCounts(loadBalancer);
    return VpcReader.listVpcs().then(
      (vpcs: IVpc[]) => this.addVpcNameToContainer(loadBalancer)(vpcs) as IAmazonLoadBalancer,
    );
  }

  public static convertClassicLoadBalancerForEditing(
    loadBalancer: IAmazonClassicLoadBalancer,
  ): IAmazonClassicLoadBalancerUpsertCommand {
    const toEdit: IAmazonClassicLoadBalancerUpsertCommand = {
      availabilityZones: undefined,
      isInternal: loadBalancer.isInternal,
      region: loadBalancer.region,
      cloudProvider: loadBalancer.cloudProvider,
      credentials: loadBalancer.credentials || loadBalancer.account,
      listeners: loadBalancer.listeners,
      loadBalancerType: 'classic',
      name: loadBalancer.name,
      regionZones: loadBalancer.availabilityZones,
      securityGroups: loadBalancer.securityGroups,
      vpcId: loadBalancer.vpcId,
      healthCheck: undefined,
      healthTimeout: loadBalancer.healthTimeout,
      healthInterval: loadBalancer.healthInterval,
      healthyThreshold: loadBalancer.healthyThreshold,
      unhealthyThreshold: loadBalancer.unhealthyThreshold,
      healthCheckProtocol: loadBalancer.healthCheckProtocol,
      healthCheckPort: loadBalancer.healthCheckPort,
      healthCheckPath: loadBalancer.healthCheckPath,
      idleTimeout: loadBalancer.idleTimeout || 60,
      subnetType: loadBalancer.subnetType,
    };

    if (loadBalancer.elb) {
      const elb = loadBalancer.elb as IClassicLoadBalancerSourceData;
      toEdit.securityGroups = elb.securityGroups;
      toEdit.vpcId = elb.vpcid || elb.vpcId;

      if (elb.listenerDescriptions) {
        toEdit.listeners = elb.listenerDescriptions.map(
          (description: any): IClassicListenerDescription => {
            const listener = description.listener;
            if (listener.sslcertificateId) {
              const splitCertificateId = listener.sslcertificateId.split('/');
              listener.sslcertificateId = splitCertificateId[1];
              listener.sslCertificateType = splitCertificateId[0].split(':')[2];
            }
            return {
              internalProtocol: listener.instanceProtocol,
              internalPort: listener.instancePort,
              externalProtocol: listener.protocol,
              externalPort: listener.loadBalancerPort,
              sslCertificateId: listener.sslcertificateId,
              sslCertificateName: listener.sslcertificateId,
              sslCertificateType: listener.sslCertificateType,
              policyNames: description.policyNames,
            };
          },
        );
      }

      if (elb.healthCheck && elb.healthCheck.target) {
        toEdit.healthTimeout = elb.healthCheck.timeout;
        toEdit.healthInterval = elb.healthCheck.interval;
        toEdit.healthyThreshold = elb.healthCheck.healthyThreshold;
        toEdit.unhealthyThreshold = elb.healthCheck.unhealthyThreshold;

        const healthCheck = elb.healthCheck.target;
        const protocolIndex = healthCheck.indexOf(':');
        let pathIndex = healthCheck.indexOf('/');

        if (pathIndex === -1) {
          pathIndex = healthCheck.length;
        }

        if (protocolIndex !== -1) {
          toEdit.healthCheckProtocol = healthCheck.substring(0, protocolIndex);
          const healthCheckPort = Number(healthCheck.substring(protocolIndex + 1, pathIndex));
          toEdit.healthCheckPath = healthCheck.substring(pathIndex);
          if (!isNaN(healthCheckPort)) {
            toEdit.healthCheckPort = healthCheckPort;
          }
        }
      }
    }
    return toEdit;
  }

  public static convertApplicationLoadBalancerForEditing(
    loadBalancer: IAmazonApplicationLoadBalancer,
  ): IAmazonApplicationLoadBalancerUpsertCommand {
    const applicationName = NameUtils.parseLoadBalancerName(loadBalancer.name).application;

    // Since we build up toEdit as we go, much easier to declare as any, then cast at return time.
    const toEdit: IAmazonApplicationLoadBalancerUpsertCommand = {
      availabilityZones: undefined,
      isInternal: loadBalancer.isInternal,
      region: loadBalancer.region,
      loadBalancerType: 'application',
      cloudProvider: loadBalancer.cloudProvider,
      credentials: loadBalancer.account || loadBalancer.credentials,
      listeners: [],
      targetGroups: [],
      name: loadBalancer.name,
      regionZones: loadBalancer.availabilityZones,
      securityGroups: [],
      subnetType: loadBalancer.subnetType,
      vpcId: undefined,
      idleTimeout: loadBalancer.idleTimeout || 60,
      deletionProtection: loadBalancer.deletionProtection || false,
      ipAddressType: loadBalancer.ipAddressType || 'ipv4',
      dualstack: loadBalancer.ipAddressType === 'dualstack',
    };

    if (loadBalancer.elb) {
      const elb = loadBalancer.elb as IApplicationLoadBalancerSourceData;
      toEdit.securityGroups = elb.securityGroups;
      toEdit.vpcId = elb.vpcid || elb.vpcId;

      // Convert listeners
      if (elb.listeners) {
        toEdit.listeners = elb.listeners.map((listener) => {
          const certificates: IALBListenerCertificate[] = [];
          if (listener.certificates) {
            listener.certificates.forEach((cert) => {
              const certArnParts = cert.certificateArn.split(':');
              const certParts = certArnParts[5].split('/');
              certificates.push({
                certificateArn: cert.certificateArn,
                type: certArnParts[2],
                name: certParts[1],
              });
            });
          }

          (listener.defaultActions || []).forEach((action) => {
            if (action.targetGroupName) {
              action.targetGroupName = action.targetGroupName.replace(`${applicationName}-`, '');
            }
            action.redirectActionConfig = action.redirectConfig;
          });

          // Remove the default rule because it already exists in defaultActions
          listener.rules = (listener.rules || []).filter((l) => !l.default);
          listener.rules.forEach((rule) => {
            (rule.actions || []).forEach((action) => {
              if (action.targetGroupName) {
                action.targetGroupName = action.targetGroupName.replace(`${applicationName}-`, '');
              }
              action.redirectActionConfig = action.redirectConfig;
            });
            (rule.conditions || []).forEach((condition) => {
              if (condition.field === 'http-request-method') {
                condition.values = condition.httpRequestMethodConfig.values;
              }
            });
            rule.conditions = rule.conditions || [];
          });

          // Sort listener.rules by priority.
          listener.rules.sort((a, b) => (a.priority as number) - (b.priority as number));

          return {
            protocol: listener.protocol,
            port: listener.port,
            defaultActions: listener.defaultActions,
            certificates,
            rules: listener.rules || [],
            sslPolicy: listener.sslPolicy,
          };
        });
      }

      // Convert target groups
      if (elb.targetGroups) {
        toEdit.targetGroups = elb.targetGroups.map((targetGroup: any) => {
          return {
            name: targetGroup.targetGroupName.replace(`${applicationName}-`, ''),
            protocol: targetGroup.protocol,
            port: targetGroup.port,
            targetType: targetGroup.targetType,
            healthCheckProtocol: targetGroup.healthCheckProtocol,
            healthCheckPort: targetGroup.healthCheckPort,
            healthCheckPath: targetGroup.healthCheckPath,
            healthCheckTimeout: targetGroup.healthCheckTimeoutSeconds,
            healthCheckInterval: targetGroup.healthCheckIntervalSeconds,
            healthyThreshold: targetGroup.healthyThresholdCount,
            unhealthyThreshold: targetGroup.unhealthyThresholdCount,
            attributes: {
              deregistrationDelay: Number(targetGroup.attributes['deregistration_delay.timeout_seconds']),
              stickinessEnabled: targetGroup.attributes['stickiness.enabled'] === 'true',
              stickinessType: targetGroup.attributes['stickiness.type'],
              stickinessDuration: Number(targetGroup.attributes['stickiness.lb_cookie.duration_seconds']),
              multiValueHeadersEnabled: targetGroup.attributes['lambda.multi_value_headers.enabled'] === 'true',
            },
          };
        });
      }
    }
    return toEdit;
  }

  public static convertNetworkLoadBalancerForEditing(
    loadBalancer: IAmazonApplicationLoadBalancer,
  ): IAmazonNetworkLoadBalancerUpsertCommand {
    const applicationName = NameUtils.parseLoadBalancerName(loadBalancer.name).application;

    // Since we build up toEdit as we go, much easier to declare as any, then cast at return time.
    const toEdit: IAmazonNetworkLoadBalancerUpsertCommand = {
      availabilityZones: undefined,
      isInternal: loadBalancer.isInternal,
      region: loadBalancer.region,
      loadBalancerType: 'network',
      cloudProvider: loadBalancer.cloudProvider,
      credentials: loadBalancer.account || loadBalancer.credentials,
      listeners: [],
      targetGroups: [],
      name: loadBalancer.name,
      regionZones: loadBalancer.availabilityZones,
      securityGroups: [],
      subnetType: loadBalancer.subnetType,
      vpcId: undefined,
      deletionProtection: loadBalancer.deletionProtection,
      loadBalancingCrossZone: loadBalancer.loadBalancingCrossZone,
      ipAddressType: loadBalancer.ipAddressType || 'ipv4',
      dualstack: loadBalancer.ipAddressType === 'dualstack',
    };

    if (loadBalancer.elb) {
      const elb = loadBalancer.elb as INetworkLoadBalancerSourceData;
      toEdit.securityGroups = elb.securityGroups;
      toEdit.vpcId = elb.vpcid || elb.vpcId;

      // Convert listeners
      if (elb.listeners) {
        toEdit.listeners = elb.listeners.map((listener) => {
          const certificates: IALBListenerCertificate[] = [];
          if (listener.certificates) {
            listener.certificates.forEach((cert) => {
              const certArnParts = cert.certificateArn.split(':');
              const certParts = certArnParts[5].split('/');
              certificates.push({
                certificateArn: cert.certificateArn,
                type: certArnParts[2],
                name: certParts[1],
              });
            });
          }

          (listener.defaultActions || []).forEach((action) => {
            if (action.targetGroupName) {
              action.targetGroupName = action.targetGroupName.replace(`${applicationName}-`, '');
            }
          });

          // Remove the default rule because it already exists in defaultActions
          listener.rules = (listener.rules || []).filter((l) => !l.default);
          listener.rules.forEach((rule) => {
            (rule.actions || []).forEach((action) => {
              if (action.targetGroupName) {
                action.targetGroupName = action.targetGroupName.replace(`${applicationName}-`, '');
              }
            });
            rule.conditions = rule.conditions || [];
          });

          // Sort listener.rules by priority.
          listener.rules.sort((a, b) => (a.priority as number) - (b.priority as number));

          return {
            protocol: listener.protocol,
            port: listener.port,
            defaultActions: listener.defaultActions,
            certificates,
            rules: listener.rules || [],
            sslPolicy: listener.sslPolicy,
          };
        });
      }

      // Convert target groups
      if (elb.targetGroups) {
        toEdit.targetGroups = elb.targetGroups.map((targetGroup: any) => {
          return {
            name: targetGroup.targetGroupName.replace(`${applicationName}-`, ''),
            protocol: targetGroup.protocol,
            port: targetGroup.port,
            targetType: targetGroup.targetType,
            healthCheckProtocol: targetGroup.healthCheckProtocol,
            healthCheckPort: targetGroup.healthCheckPort,
            healthCheckTimeout: targetGroup.healthCheckTimeoutSeconds,
            healthCheckInterval: targetGroup.healthCheckIntervalSeconds,
            healthyThreshold: targetGroup.healthyThresholdCount,
            unhealthyThreshold: targetGroup.unhealthyThresholdCount,
            healthCheckPath: targetGroup.healthCheckPath,
            attributes: {
              deregistrationDelay: Number(targetGroup.attributes['deregistration_delay.timeout_seconds']),
              deregistrationDelayConnectionTermination: Boolean(
                targetGroup.attributes['deregistration_delay.connection_termination.enabled'] === 'true',
              ),
              preserveClientIp: Boolean(targetGroup.attributes['preserve_client_ip.enabled'] === 'true'),
            },
          };
        });
      }
    }
    return toEdit;
  }

  public static constructNewClassicLoadBalancerTemplate(
    application: Application,
  ): IAmazonClassicLoadBalancerUpsertCommand {
    const defaultCredentials = application.defaultCredentials.aws || AWSProviderSettings.defaults.account;
    const defaultRegion = application.defaultRegions.aws || AWSProviderSettings.defaults.region;
    const defaultSubnetType = AWSProviderSettings.defaults.subnetType;
    return {
      availabilityZones: undefined,
      name: '',
      stack: '',
      detail: '',
      loadBalancerType: 'classic',
      isInternal: false,
      cloudProvider: 'aws',
      credentials: defaultCredentials,
      region: defaultRegion,
      vpcId: null,
      subnetType: defaultSubnetType,
      healthCheck: undefined,
      healthCheckProtocol: 'HTTP',
      healthCheckPort: 7001,
      healthCheckPath: '/healthcheck',
      healthTimeout: 5,
      healthInterval: 10,
      healthyThreshold: 10,
      unhealthyThreshold: 2,
      idleTimeout: 60,
      regionZones: [],
      securityGroups: [],
      listeners: [
        {
          externalPort: 80,
          externalProtocol: 'HTTP',
          internalPort: 7001,
          internalProtocol: 'HTTP',
        },
      ],
    };
  }

  public static constructNewApplicationLoadBalancerTemplate(
    application: Application,
  ): IAmazonApplicationLoadBalancerUpsertCommand {
    const defaultCredentials = application.defaultCredentials.aws || AWSProviderSettings.defaults.account;
    const defaultRegion = application.defaultRegions.aws || AWSProviderSettings.defaults.region;
    const defaultSubnetType = AWSProviderSettings.defaults.subnetType;
    const defaultPort = application.attributes.instancePort || SETTINGS.defaultInstancePort;
    const defaultTargetGroupName = `targetgroup`;
    return {
      name: '',
      availabilityZones: undefined,
      stack: '',
      detail: '',
      loadBalancerType: 'application',
      ipAddressType: 'ipv4',
      dualstack: false,
      isInternal: false,
      cloudProvider: 'aws',
      credentials: defaultCredentials,
      region: defaultRegion,
      vpcId: null,
      subnetType: defaultSubnetType,
      idleTimeout: 60,
      deletionProtection: false,
      targetGroups: [
        {
          name: defaultTargetGroupName,
          protocol: 'HTTP',
          port: defaultPort,
          targetType: 'instance',
          healthCheckProtocol: 'HTTP',
          healthCheckPort: 'traffic-port',
          healthCheckPath: '/healthcheck',
          healthCheckTimeout: 5,
          healthCheckInterval: 10,
          healthyThreshold: 10,
          unhealthyThreshold: 2,
          attributes: {
            deregistrationDelay: 300,
            stickinessEnabled: false,
            stickinessType: 'lb_cookie',
            stickinessDuration: 8400,
            multiValueHeadersEnabled: false,
          },
        },
      ],
      regionZones: [],
      securityGroups: [],
      listeners: [
        {
          certificates: [],
          protocol: 'HTTP',
          port: 80,
          defaultActions: [
            {
              type: 'forward',
              targetGroupName: defaultTargetGroupName,
            },
          ],
          rules: [],
        },
      ],
    };
  }

  public static constructNewNetworkLoadBalancerTemplate(
    application: Application,
  ): IAmazonNetworkLoadBalancerUpsertCommand {
    const defaultCredentials = application.defaultCredentials.aws || AWSProviderSettings.defaults.account;
    const defaultRegion = application.defaultRegions.aws || AWSProviderSettings.defaults.region;
    const defaultSubnetType = AWSProviderSettings.defaults.subnetType;
    const defaultTargetGroupName = `targetgroup`;
    return {
      name: '',
      availabilityZones: undefined,
      stack: '',
      detail: '',
      loadBalancerType: 'network',
      isInternal: false,
      ipAddressType: 'ipv4',
      dualstack: false,
      cloudProvider: 'aws',
      credentials: defaultCredentials,
      region: defaultRegion,
      vpcId: null,
      subnetType: defaultSubnetType,
      deletionProtection: false,
      loadBalancingCrossZone: true,
      securityGroups: [],
      targetGroups: [
        {
          name: defaultTargetGroupName,
          protocol: 'TCP',
          port: 7001,
          targetType: 'instance',
          healthCheckProtocol: 'TCP',
          healthCheckPath: '/healthcheck',
          healthCheckPort: 'traffic-port',
          healthCheckTimeout: 5,
          healthCheckInterval: 10,
          healthyThreshold: 10,
          unhealthyThreshold: 10,
          attributes: {
            deregistrationDelay: 300,
          },
        },
      ],
      regionZones: [],
      listeners: [
        {
          certificates: [],
          protocol: 'TCP',
          port: 80,
          defaultActions: [
            {
              type: 'forward',
              targetGroupName: defaultTargetGroupName,
            },
          ],
          rules: [],
        },
      ],
    };
  }
}
