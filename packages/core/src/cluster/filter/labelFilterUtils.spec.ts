import {
  buildLabelsMap,
  labelFiltersToTrueKeyObject,
  trueKeyObjectToLabelFilters,
  ILabelFilter,
} from './labelFilterUtils';
import { IServerGroup } from '../../domain/IServerGroup';

describe('Label filter utils', () => {
  let labelFilters: ILabelFilter[];
  let labelFiltersAsTrueKeyObj: { [key: string]: boolean };
  let serverGroups: IServerGroup[];
  beforeEach(() => {
    labelFilters = [
      {
        key: 'key1',
        value: 'value1',
      },
      {
        key: 'key2',
        value: 'value2',
      },
    ];
    labelFiltersAsTrueKeyObj = {
      'key1:value1': true,
      'key2:value2': true,
    };
    serverGroups = [
      {
        account: 'my-account',
        cloudProvider: 'kubernetes',
        cluster: 'my-cluster',
        instanceCounts: {
          up: 0,
          down: 0,
          starting: 0,
          succeeded: 0,
          failed: 0,
          unknown: 0,
          outOfService: 0,
        },
        instances: [],
        labels: {
          key1: 'value1',
          key2: 'value2',
        },
        name: 'server-group-1',
        region: 'us-east1-b',
        type: 'kubernetes',
      },
      {
        account: 'my-account',
        cloudProvider: 'kubernetes',
        cluster: 'my-cluster',
        instanceCounts: {
          up: 0,
          down: 0,
          starting: 0,
          succeeded: 0,
          failed: 0,
          unknown: 0,
          outOfService: 0,
        },
        instances: [],
        labels: {
          key1: 'value2',
          key3: 'value1',
        },
        name: 'server-group-2',
        region: 'us-east1-b',
        type: 'kubernetes',
      },
    ];
  });
  describe('buildLabelsMap', () => {
    it('Builds map of existing label keys to values among given server groups', () => {
      expect(buildLabelsMap(serverGroups)).toEqual({
        key1: ['value1', 'value2'],
        key2: ['value2'],
        key3: ['value1'],
      });
    });
  });
  describe('labelFiltersToTrueKeyObject', () => {
    it('Converts list of label filters to object by transforming each set of key-value pairs into a key', () => {
      expect(labelFiltersToTrueKeyObject(labelFilters)).toEqual(labelFiltersAsTrueKeyObj);
    });
  });
  describe('trueKeyObjectToLabelFilters', () => {
    it('Converts { [key:string]: boolean } object to list of label filters by parsing each key as a set of key-value pairs', () => {
      expect(trueKeyObjectToLabelFilters(labelFiltersAsTrueKeyObj)).toEqual(labelFilters);
    });
  });
});
