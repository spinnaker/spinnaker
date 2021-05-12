import { BasePluginManager } from '../plugins/BasePluginManager';
import { IconNames } from '../../presentation';

const UNKNOWN_RESOURCE_ICON = 'placeholder';

export interface IResourceKindConfig {
  kind: string;
  iconName: IconNames;
  experimentalDisplayLink?: (resource: IResourceLinkProps) => string;
}

export interface IResourceLinkProps {
  kind: string;
  account?: string;
  stack?: string;
  detail?: string;
  displayName?: string;
}

class ResourcesManager extends BasePluginManager<IResourceKindConfig> {
  public getIcon(kind: string) {
    return this.getHandler(kind)?.iconName ?? UNKNOWN_RESOURCE_ICON;
  }

  public getExperimentalDisplayLink(resource: IResourceLinkProps): string | undefined {
    return this.getHandler(resource.kind)?.experimentalDisplayLink?.(resource);
  }

  // Returns the base "spinnaker" type. e.g. ec2/cluster@1.1 -> cluster
  public getSpinnakerType(kind: string): string {
    const normalizedKind = this.normalizeKind(kind);
    const spinnakerType = normalizedKind.split('/')?.[1];
    return spinnakerType || normalizedKind;
  }

  public getNativeResourceRoutingInfo({
    kind,
    account,
    stack,
    detail,
    displayName,
  }: IResourceLinkProps): { state: string; params: { [key in string]?: string } } | undefined {
    const kindName = this.getSpinnakerType(kind);
    const params = {
      acct: account,
      stack,
      detail,
      q: displayName,
    };

    switch (kindName) {
      case 'cluster':
        return { state: 'home.applications.application.insight.clusters', params };

      case 'security-group':
        return { state: 'home.applications.application.insight.firewalls', params };

      case 'classic-load-balancer':
      case 'application-load-balancer':
        return { state: 'home.applications.application.insight.loadBalancers', params };
    }

    return undefined;
  }
}

const DEFAULT_RESOURCES: IResourceKindConfig[] = [
  {
    kind: 'titus/cluster',
    iconName: 'cluster',
  },
  {
    kind: 'ec2/cluster',
    iconName: 'cluster',
  },
  {
    kind: 'ec2/security-group',
    iconName: 'securityGroup',
  },
  {
    kind: 'ec2/classic-load-balancer',
    iconName: 'loadBalancer',
  },
  {
    kind: 'ec2/application-load-balancer',
    iconName: 'loadBalancer',
  },
];

// TODO: this should not be global - convert it to React Context
export const resourceManager = new ResourcesManager(DEFAULT_RESOURCES);
