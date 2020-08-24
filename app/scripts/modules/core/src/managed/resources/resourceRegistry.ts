import { IconNames } from '../../presentation';

const UNKNOWN_RESOURCE_ICON = 'placeholder';

const resourceConfigsByKind: { [kind: string]: IResourceKindConfig } = {};

export interface IResourceKindConfig {
  kind: string;
  iconName: IconNames;
}

export const isResourceKindSupported = (kind: string) => resourceConfigsByKind.hasOwnProperty(kind);

export const registerResourceKind = (config: IResourceKindConfig) => {
  resourceConfigsByKind[config.kind] = config;
};

export const getResourceIcon = (kind: string) => resourceConfigsByKind[kind]?.iconName ?? UNKNOWN_RESOURCE_ICON;
