import { reduce } from 'lodash';

import { IServerGroup } from '../../domain/IServerGroup';

export interface ILabelsMap {
  [key: string]: string[];
}

export interface ILabelFilter {
  key: string;
  value: string;
}

export function buildLabelsMap(serverGroups: IServerGroup[]): ILabelsMap {
  return reduce(
    serverGroups,
    (map: ILabelsMap, serverGroup) => {
      const labelKeys = Object.keys(serverGroup.labels || {});
      labelKeys.forEach((key) => {
        if (!map[key]) {
          map[key] = [];
        }
        if (!map[key].includes(serverGroup.labels[key])) {
          map[key].push(serverGroup.labels[key]);
        }
      });
      return map;
    },
    {},
  );
}

export function labelFiltersToTrueKeyObject(labelFilters: ILabelFilter[]): { [key: string]: boolean } {
  if (!labelFilters) {
    return {};
  }
  return reduce(
    labelFilters,
    (trueKeyObj: { [key: string]: boolean }, labelFilter) => {
      const key = `${labelFilter.key}:${labelFilter.value}`;
      trueKeyObj[key] = true;
      return trueKeyObj;
    },
    {},
  );
}

export function trueKeyObjectToLabelFilters(trueKeyObject: { [key: string]: boolean }): ILabelFilter[] {
  if (!trueKeyObject) {
    return [];
  }
  return reduce(
    trueKeyObject,
    (labelFilters, _val, composedKey) => {
      let [key, value] = composedKey.split(':');
      if (key === 'null') {
        key = null;
      }
      if (value === 'null') {
        value = null;
      }
      return labelFilters.concat([{ key, value }]);
    },
    [],
  );
}
