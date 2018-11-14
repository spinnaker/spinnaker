import { IManifestLabelSelectors } from './IManifestLabelSelector';

export interface IMultiManifestSelector extends IManifestSelector {
  kinds: string[];
  labelSelectors?: IManifestLabelSelectors;
}

export interface IManifestSelector {
  manifestName?: string;
  kind?: string;
  location: string;
  account: string;
  cluster?: string;
  criteria?: string;
  mode?: SelectorMode;
  app?: string;
}

export enum SelectorMode {
  Static = 'static',
  Dynamic = 'dynamic', // TODO(dpeach): add 'Label' mode.
}
