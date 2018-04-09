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
}
