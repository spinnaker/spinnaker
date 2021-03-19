import { IManagedResourceSummary } from '../../domain';
import { BasePluginManager } from '../plugins/BasePluginManager';
import { IconNames } from '../../presentation';

const UNKNOWN_RESOURCE_ICON = 'placeholder';

export interface IResourceKindConfig {
  kind: string;
  iconName: IconNames;
  // Short-term way of making custom links on the client for each resource.
  // Soon we'll add a details drawer that all resource kinds will open when clicked,
  // and each kind will implement their details drawer with any relevant links/pointers.
  // This should be removed when that work is complete.
  experimentalDisplayLink?: (resource: IManagedResourceSummary) => string;
}

class ResourcesManager extends BasePluginManager<IResourceKindConfig> {
  public getResourceIcon(kind: string) {
    return this.getHandler(kind)?.iconName ?? UNKNOWN_RESOURCE_ICON;
  }

  public getExperimentalDisplayLink(resource: IManagedResourceSummary): string | undefined {
    return this.getHandler(resource.kind)?.experimentalDisplayLink?.(resource);
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
