import { IManagedResourceSummary } from '../../domain';
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

class ResourcesManager {
  private resourceConfigs: { [kind: string]: IResourceKindConfig } = {};

  constructor(resources: IResourceKindConfig[]) {
    for (const resource of resources) {
      this.registerResource(resource);
    }
  }

  private normalizeKind(kind: string): string {
    // Removes the version of the resource
    return kind.split('@')[0];
  }

  public getResource(kind: string): IResourceKindConfig | undefined {
    // We first try to return an exact match, otherwise, we return the resource without the version
    return this.resourceConfigs[kind] || this.resourceConfigs[this.normalizeKind(kind)];
  }

  public isResourceSupported(kind: string) {
    return Boolean(this.getResource(kind));
  }

  public registerResource(config: IResourceKindConfig) {
    // We register both the resource with the version and without the version
    this.resourceConfigs[config.kind] = config;
    this.resourceConfigs[this.normalizeKind(config.kind)] = config;
  }

  public getResourceIcon(kind: string) {
    return this.getResource(kind)?.iconName ?? UNKNOWN_RESOURCE_ICON;
  }

  public getExperimentalDisplayLink(resource: IManagedResourceSummary): string | undefined {
    return this.getResource(resource.kind)?.experimentalDisplayLink?.(resource);
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
