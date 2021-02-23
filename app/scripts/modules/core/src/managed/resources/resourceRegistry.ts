import { IManagedResourceSummary } from '../../domain';
import { IconNames } from '../../presentation';

const UNKNOWN_RESOURCE_ICON = 'placeholder';

const resourceConfigsByKind: { [kind: string]: IResourceKindConfig } = {};

export interface IResourceKindConfig {
  kind: string;
  iconName: IconNames;
  // Short-term way of making custom links on the client for each resource.
  // Soon we'll add a details drawer that all resource kinds will open when clicked,
  // and each kind will implement their details drawer with any relevant links/pointers.
  // This should be removed when that work is complete.
  experimentalDisplayLink?: (resource: IManagedResourceSummary) => string;
}

export const isResourceKindSupported = (kind: string) => resourceConfigsByKind.hasOwnProperty(kind);

export const registerResourceKind = (config: IResourceKindConfig) => {
  resourceConfigsByKind[config.kind] = config;
};

export const getResourceIcon = (kind: string) => resourceConfigsByKind[kind]?.iconName ?? UNKNOWN_RESOURCE_ICON;

export const getExperimentalDisplayLink = (resource: IManagedResourceSummary) =>
  resourceConfigsByKind[resource.kind]?.experimentalDisplayLink?.(resource) ?? null;
