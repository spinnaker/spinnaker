import { fireEvent, render, waitFor, within } from '@testing-library/react';
import React from 'react';

import type { Application, IMoniker, IServerGroup } from '@spinnaker/core';

import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import type { IAccountRegionClusterSelectorProps } from './AccountRegionClusterSelector';
import { AccountRegionClusterSelector } from './AccountRegionClusterSelector';

describe('<AccountRegionClusterSelector />', () => {
  let application: Application;
  const noop = () => {};

  function createServerGroup(account: string, cluster: string, name: string, region: string): IServerGroup {
    return {
      account,
      cloudProvider: 'cloud-provider',
      cluster,
      name,
      region,
      instances: [{ health: null, id: 'instance-id', launchTime: 0, name: 'instance-name', zone: 'GMT' }],
      instanceCounts: { up: 1, down: 0, starting: 0, succeeded: 1, failed: 0, unknown: 0, outOfService: 0 },
      moniker: { app: 'my-app', cluster, detail: 'my-detail', stack: 'my-stack', sequence: 1 },
    } as IServerGroup;
  }

  function getSelect(label: string): HTMLInputElement {
    return within(getFormGroupByLabel(label)).getByRole('combobox') as HTMLInputElement;
  }

  async function selectOption(label: string, value: string): Promise<void> {
    const field = getFormGroupByLabel(label);
    const input = within(field).getByRole('combobox');
    fireEvent.mouseDown(input);
    fireEvent.change(input, { target: { value } });
    await waitFor(() => {
      const match = within(field)
        .getAllByRole('option')
        .find((candidate) => candidate.closest('.Select-menu') && candidate.textContent?.includes(value));
      expect(match).toBeDefined();
    });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13 });
  }

  async function expectSelectOptions(label: string, options: string[]): Promise<void> {
    const field = getFormGroupByLabel(label);
    fireEvent.mouseDown(within(field).getByRole('combobox'));
    await within(field).findByRole('listbox');
    options.forEach((option) =>
      expect(within(field).getAllByRole('option', { name: option }).length).toBeGreaterThan(0),
    );
  }

  beforeEach(() => {
    const serverGroups = [
      createServerGroup('account-name-one', 'app-stack-detailOne', 'app', 'region-one'),
      createServerGroup('account-name-two', 'app-stack-detailTwo', 'app', 'region-two'),
      createServerGroup('account-name-one', 'app-stack-detailOne', 'app', 'region-three'),
      createServerGroup('account-name-one', 'app-stack-detailThree', 'app', 'region-one'),
      createServerGroup('account-name-one', 'app-stack-detailFour', 'app', 'region-three'),
      createServerGroup('account-name-one', 'app-stack-detailFive', 'app', 'region-two'),
    ];
    application = {
      ready: () => Promise.resolve(),
      getDataSource: () => ({ data: serverGroups }),
    } as Application;
  });

  it('initializes properly with provided component', async () => {
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: noop,
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await expectSelectOptions('Region', ['region-one', 'region-two', 'region-three']);
    await expectSelectOptions('Cluster', ['app-stack-detailOne', 'app-stack-detailThree']);
    expect(document.querySelector('input[name="credentials"]')).toBeInTheDocument();
  });

  it('retrieves the correct list of regions when account is changed', async () => {
    let credentials = '';
    let region = 'SHOULD-CHANGE';
    let regions = ['SHOULD-CHANGE'];
    let cluster = 'SHOULD-CHANGE';
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: (value: any) => {
        credentials = value.credentials;
        region = value.region;
        regions = value.regions;
        cluster = value.cluster;
      },
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await expectSelectOptions('Region', ['region-one', 'region-two', 'region-three']);
    await expectSelectOptions('Cluster', ['app-stack-detailOne', 'app-stack-detailThree']);
    await selectOption('Account', 'account-name-two');

    await expectSelectOptions('Region', ['region-two']);
    expect(region).toEqual('', 'selected region is not cleared');
    expect(regions.length).toBe(0, 'selected regions list is not cleared');
    expect(credentials).toContain('account-name-two');
    expect(cluster).toBeUndefined('selected cluster is not cleared');
  });

  it('retrieves the correct list of clusters when the selector is multi-region and the region is changed', async () => {
    let regions: string[] = [];
    let cluster = 'SHOULD-CHANGE';
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      clusterField: 'newCluster',
      onComponentUpdate: (value: any) => {
        regions = value.regions;
        cluster = value.newCluster;
      },
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await selectOption('Region', 'region-three');

    await expectSelectOptions('Cluster', ['app-stack-detailOne', 'app-stack-detailThree', 'app-stack-detailFour']);
    expect(cluster).toBeUndefined('selected cluster is not cleared');
    expect(regions.length).toBe(2);
    expect(regions).toContain('region-one');
    expect(regions).toContain('region-three');
  });

  it('retrieves the correct list of clusters on startup and the selector is single-region', async () => {
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: (_value: any) => {},
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        region: 'region-one',
      },
      isSingleRegion: true,
    };

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await expectSelectOptions('Region', ['region-one', 'region-two', 'region-three']);
    await expectSelectOptions('Cluster', ['app-stack-detailOne', 'app-stack-detailThree']);
  });

  it('the cluster value is updated in the component when cluster is changed', async () => {
    let cluster = '';
    let moniker: IMoniker = { app: '' };
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      clusterField: 'newCluster',
      onComponentUpdate: (value: any) => {
        cluster = value.newCluster;
        moniker = value.moniker;
      },
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const expectedMoniker = {
      app: 'my-app',
      cluster: 'app-stack-detailThree',
      detail: 'my-detail',
      stack: 'my-stack',
      sequence: null,
    } as IMoniker;

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await selectOption('Cluster', 'app-stack-detailThree');

    expect(cluster).toBe('app-stack-detailThree');
    expect(moniker).toEqual(expectedMoniker);
  });

  it('the cluster value is updated in the component when cluster is changed to freeform value', async () => {
    let cluster = '';
    let moniker: IMoniker = { app: '' };
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      clusterField: 'newCluster',
      onComponentUpdate: (value: any) => {
        cluster = value.newCluster;
        moniker = value.moniker;
      },
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await selectOption('Cluster', 'app-stack-freeform');

    expect(cluster).toBe('app-stack-freeform');
    expect(moniker).toBeUndefined();
  });

  it('initialize with form names', async () => {
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: noop,
      componentName: 'form',
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    render(<AccountRegionClusterSelector {...accountRegionClusterProps} />);

    await waitFor(() => {
      expect(document.querySelector('input[name="form.credentials"]')).toBeInTheDocument();
      expect(document.querySelector('input[name="form.regions"]')).toBeInTheDocument();
      expect(document.querySelector('input[name="form.cluster"]')).toBeInTheDocument();
    });
  });
});
