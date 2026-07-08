import { shallow } from 'enzyme';
import React from 'react';

import type { Application, IAccountDetails, IMoniker, IServerGroup } from '@spinnaker/core';

import {
  getClusterSelectorState,
  getRegionNamesWithAccountFallback,
  TencentcloudAccountRegionClusterSelector,
} from './TencentcloudAccountRegionClusterSelector';

describe('<TencentcloudAccountRegionClusterSelector />', () => {
  it('does not mutate the cached server group moniker when storing the selected cluster moniker', () => {
    const cachedMoniker: IMoniker = { app: 'fnord', cluster: 'fnord-prod', stack: 'prod', sequence: 7 };
    const application = {
      ready: () => Promise.resolve(),
      getDataSource: () => ({
        data: [{ account: 'test', cluster: 'fnord-prod', moniker: cachedMoniker, region: 'us-east-1' } as IServerGroup],
      }),
    } as Application;
    const setFieldValue = jasmine.createSpy('setFieldValue');

    const component = shallow(
      React.createElement(TencentcloudAccountRegionClusterSelector, {
        accounts: [],
        application,
        component: { credentials: 'test', regions: ['us-east-1'] },
        setFieldValue,
      }),
    );

    component.find('select').simulate('change', { target: { value: 'fnord-prod' } });

    expect(cachedMoniker.sequence).toBe(7);
    expect(setFieldValue).toHaveBeenCalledWith('moniker', { ...cachedMoniker, sequence: null });
    expect(setFieldValue.calls.mostRecent().args[1]).not.toBe(cachedMoniker);
  });

  it('updates custom cluster text without recalculating the moniker until blur', () => {
    const cachedMoniker: IMoniker = { app: 'fnord', cluster: 'fnord-prod', stack: 'prod', sequence: 7 };
    const application = {
      ready: () => Promise.resolve(),
      getDataSource: () => ({
        data: [{ account: 'test', cluster: 'fnord-prod', moniker: cachedMoniker, region: 'us-east-1' } as IServerGroup],
      }),
    } as Application;
    const setFieldValue = jasmine.createSpy('setFieldValue');
    const component = shallow(
      React.createElement(TencentcloudAccountRegionClusterSelector, {
        accounts: [],
        application,
        component: { credentials: 'test', regions: ['us-east-1'] },
        setFieldValue,
      }),
    );

    component.find('a').simulate('click', { preventDefault: jasmine.createSpy('preventDefault') });
    setFieldValue.calls.reset();
    component.find('input').simulate('change', { target: { value: 'fnord' } });

    expect(setFieldValue.calls.allArgs()).toEqual([['cluster', 'fnord']]);

    component.find('input').simulate('blur', { target: { value: 'fnord-prod' } });

    expect(setFieldValue).toHaveBeenCalledWith('moniker', { ...cachedMoniker, sequence: null });
  });
});

describe('getRegionNamesWithAccountFallback', () => {
  const accounts = [
    {
      name: 'test',
      regions: [{ name: 'us-east-1' }, { name: 'eu-west-1' }],
    },
  ] as IAccountDetails[];

  it('uses application regions when clusters provide them', () => {
    expect(getRegionNamesWithAccountFallback(['us-west-2'], accounts, 'test', ['us-west-2'])).toEqual(['us-west-2']);
  });

  it('falls back to account regions when the application has no regions for the account', () => {
    expect(getRegionNamesWithAccountFallback([], accounts, 'test', [])).toEqual(['eu-west-1', 'us-east-1']);
  });

  it('falls back to account regions when a selected region is missing from application regions', () => {
    expect(getRegionNamesWithAccountFallback(['us-west-2'], accounts, 'test', ['eu-west-1'])).toEqual([
      'eu-west-1',
      'us-east-1',
    ]);
  });

  it('uses account regions for custom cluster input even when application regions exist', () => {
    expect(getRegionNamesWithAccountFallback(['us-east-1'], accounts, 'test', [], true)).toEqual([
      'eu-west-1',
      'us-east-1',
    ]);
  });
});

describe('getClusterSelectorState', () => {
  it('preserves custom input mode when a typed value matches an existing cluster', () => {
    expect(getClusterSelectorState(['prod'], 'prod', true)).toEqual({
      clusters: ['prod'],
      isCustomClusterInput: true,
    });
  });

  it('restores custom input mode for a persisted custom cluster name', () => {
    expect(getClusterSelectorState(['prod'], 'custom', false)).toEqual({
      clusters: ['prod', 'custom'],
      isCustomClusterInput: true,
    });
  });
});
