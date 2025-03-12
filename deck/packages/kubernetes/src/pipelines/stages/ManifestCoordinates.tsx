import { flatten, get } from 'lodash';
import React from 'react';

import { StageConstants } from '@spinnaker/core';

export interface IManifestCoordinateProps {
  account: string;
  manifestName: string;
  location: string;
  cluster: string;
  criteria: string;
  labelSelectors: ILabelSelectors;
  manifestNamesByNamespace: IManifestNamesByNamespace;
}

export interface IManifestNamesByNamespace {
  [namespace: string]: string[];
}

export interface ILabelSelectors {
  selectors: Array<{
    key?: string;
    kind: SelectorKind;
    values?: string[];
  }>;
}

export enum SelectorKind {
  ANY = 'ANY',
  EQUALS = 'EQUALS',
  NOT_EQUALS = 'NOT_EQUALS',
  CONTAINS = 'CONTAINS',
  NOT_CONTAINS = 'NOT_CONTAINS',
  EXISTS = 'EXISTS',
  NOT_EXISTS = 'NOT_EXISTS',
}

const mapCriteriaToLabel = (criteria: string): string =>
  get(
    StageConstants.MANIFEST_CRITERIA_OPTIONS.find((option) => option.val === criteria),
    'label',
  );

export const formatLabelSelectors = (labelSelectors: ILabelSelectors): string => {
  return (labelSelectors.selectors || [])
    .map((selector) => {
      const { key, kind, values = [] } = selector;
      switch (kind) {
        case SelectorKind.ANY:
          return null;
        case SelectorKind.EQUALS:
          return `${key} = ${values[0]}`;
        case SelectorKind.NOT_EQUALS:
          return `${key} != ${values[0]}`;
        case SelectorKind.CONTAINS:
          return `${key} in (${values.join(', ')})`;
        case SelectorKind.NOT_CONTAINS:
          return `${key} notin (${values.join(', ')})`;
        case SelectorKind.EXISTS:
          return `${key}`;
        case SelectorKind.NOT_EXISTS:
          return `!${key}`;
        default:
          return null;
      }
    })
    .filter((formatted) => !!formatted)
    .join(', ');
};

const formatManifestNames = (manifestName: string, manifestNamesByNamespace: IManifestNamesByNamespace) => {
  if (manifestName) {
    return (
      <>
        <dt>Manifest</dt>
        <dd>{manifestName}</dd>
      </>
    );
  } else if (manifestNamesByNamespace) {
    const names = flatten(Object.values(manifestNamesByNamespace));
    return (
      <>
        <dt>
          Manifest
          {names.length > 1 ? 's' : ''}
        </dt>
        <dd>{names.join(', ')}</dd>
      </>
    );
  } else {
    return null;
  }
};

export const ManifestCoordinates = ({
  account,
  manifestName,
  location,
  cluster,
  criteria,
  labelSelectors,
  manifestNamesByNamespace,
}: IManifestCoordinateProps) => {
  return (
    <>
      <dt>Account</dt>
      <dd>{account}</dd>
      {formatManifestNames(manifestName, manifestNamesByNamespace)}
      <dt>Namespace</dt>
      <dd>{location}</dd>
      {mapCriteriaToLabel(criteria) != null && cluster != null && (
        <>
          <dt>Target</dt>
          <dd>{`${mapCriteriaToLabel(criteria)} in cluster ${cluster}`}</dd>
        </>
      )}
      {labelSelectors != null && !!formatLabelSelectors(labelSelectors) && (
        <>
          <dt>
            Selector
            {(labelSelectors.selectors || []).length > 1 ? 's' : ''}
          </dt>
          <dd>{formatLabelSelectors(labelSelectors)}</dd>
        </>
      )}
    </>
  );
};
