import { IManifestLabelSelectors } from './IManifestLabelSelector';

export interface IMultiManifestSelector extends IManifestSelector {
  kinds: string[];
  labelSelectors?: IManifestLabelSelectors;
}

export interface IManifestSelector {
  account: string;
  app?: string;
  cluster?: string;
  criteria?: string;
  kind?: string;
  kinds?: string[];
  labelSelectors?: IManifestLabelSelectors;
  location: string;
  manifestName?: string;
  mode?: SelectorMode;
}

export enum SelectorMode {
  Static = 'static',
  Dynamic = 'dynamic',
  Label = 'label',
}

interface ISelectorModeData {
  label: string;
  selectorDefaults: Partial<IManifestSelector>;
}

export const SelectorModeDataMap: { [key in SelectorMode]: ISelectorModeData } = {
  [SelectorMode.Static]: {
    label: 'Choose a static target',
    selectorDefaults: {
      cluster: null,
      criteria: null,
      kind: null,
      kinds: null,
      labelSelectors: null,
    },
  },
  [SelectorMode.Dynamic]: {
    label: 'Choose a target dynamically',
    selectorDefaults: {
      kinds: null,
      labelSelectors: null,
      manifestName: null,
    },
  },
  [SelectorMode.Label]: {
    label: 'Match target(s) by label',
    selectorDefaults: {
      cluster: null,
      criteria: null,
      kind: null,
      kinds: [],
      labelSelectors: {
        selectors: [],
      },
      manifestName: null,
    },
  },
};
