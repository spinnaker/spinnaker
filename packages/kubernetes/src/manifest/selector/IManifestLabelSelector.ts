export interface IManifestLabelSelector {
  key: string;
  kind: string;
  values: string[];
}

export interface IManifestLabelSelectors {
  selectors: IManifestLabelSelector[];
}

export const LABEL_KINDS: string[] = [
  'ANY',
  'EQUALS',
  'NOT_EQUALS',
  'CONTAINS',
  'NOT_CONTAINS',
  'EXISTS',
  'NOT_EXISTS',
];
