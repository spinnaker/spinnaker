import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { AccountService } from '../account/AccountService';
import { getFormGroupByLabel } from '../utils/testUtils/rtl';
import { AccountRegionClusterSelector } from './AccountRegionClusterSelector';

describe('AccountRegionClusterSelector', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue([
      'us-east-1',
      { 'us-west-2': ['us-west-2a'] },
    ] as any);
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([] as any);
  });

  const regionSelect = () => within(getFormGroupByLabel('Region')).getByRole('combobox');
  const clusterGroup = () => within(getFormGroupByLabel('Cluster'));
  const accountSelect = () => within(getFormGroupByLabel('Account')).getByRole('combobox');

  it('renders the native selector and defaults the cluster field', async () => {
    render(
      <AccountRegionClusterSelector
        accounts={[]}
        application={applicationWithServerGroups([])}
        component={{ cloudProviderType: 'aws', credentials: 'test' }}
      />,
    );

    expect(await clusterGroup().findByRole('combobox')).toBeInTheDocument();
  });

  it('normalizes fetched regions and clears invalid clusters after region changes', async () => {
    const onComponentUpdate = vi.fn();
    const component = {
      cloudProviderType: 'aws',
      credentials: 'test',
      region: 'us-east-1',
      cluster: 'app-main',
    } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 4 } },
      { account: 'test', region: 'us-west-2', cluster: 'app-west', moniker: { cluster: 'app-west', sequence: 1 } },
    ]);
    render(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={component}
        onComponentUpdate={onComponentUpdate}
        singleRegion={true as any}
      />,
    );

    expect(await within(regionSelect()).findByRole('option', { name: 'us-west-2' })).toBeInTheDocument();
    await userEvent.selectOptions(regionSelect(), 'us-west-2');

    expect(component.cluster).toBeUndefined();
    expect(onComponentUpdate).toHaveBeenCalledWith(component);
  });

  it('immediately publishes the exact component after selecting a cluster', async () => {
    const onComponentUpdate = vi.fn();
    const component = { cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);
    render(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={component}
        onComponentUpdate={onComponentUpdate}
        singleRegion={true as any}
      />,
    );

    await waitFor(() => expect(clusterGroup().getByRole('option', { name: 'app-main' })).toBeInTheDocument());
    await userEvent.selectOptions(clusterGroup().getByRole('combobox'), 'app-main');

    expect(onComponentUpdate).toHaveBeenCalledTimes(1);
    expect(onComponentUpdate).toHaveBeenLastCalledWith({
      cloudProviderType: 'aws',
      credentials: 'test',
      region: 'us-east-1',
      cluster: 'app-main',
      moniker: { cluster: 'app-main', sequence: null },
    });
  });

  it('notifies account changes', async () => {
    const onAccountUpdate = vi.fn();
    const onComponentUpdate = vi.fn();
    const component = { cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' } as any;
    render(
      <AccountRegionClusterSelector
        accounts={['test', 'prod']}
        application={applicationWithServerGroups([])}
        component={component}
        onAccountUpdate={onAccountUpdate}
        onComponentUpdate={onComponentUpdate}
        singleRegion={true as any}
      />,
    );

    await waitFor(() => expect(within(accountSelect()).getByRole('option', { name: 'prod' })).toBeInTheDocument());
    await userEvent.selectOptions(accountSelect(), 'prod');
    expect(component.credentials).toBe('prod');
    expect(component.cluster).toBeUndefined();
    expect(onAccountUpdate).toHaveBeenCalledWith('prod');
    expect(onComponentUpdate).toHaveBeenCalledWith(component);
  });

  it('renders expression credentials as runtime-resolved account text', async () => {
    render(
      <AccountRegionClusterSelector
        accounts={['test', 'prod']}
        application={applicationWithServerGroups([])}
        component={{ cloudProviderType: 'aws', credentials: '${parameters.account}' }}
      />,
    );

    expect(screen.getByText(/Resolved at runtime from expression/)).toHaveTextContent('${parameters.account}');
    expect(within(getFormGroupByLabel('Account')).queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('supports entering arbitrary cluster text while using all provider regions', async () => {
    let component = { cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);
    const Harness = () => {
      const [model, setModel] = React.useState(component);
      return (
        <AccountRegionClusterSelector
          accounts={[]}
          application={application}
          component={model}
          onComponentUpdate={(updated) => {
            component = { ...updated };
            setModel(component);
          }}
          singleRegion={true as any}
        />
      );
    };
    render(<Harness />);

    await userEvent.click(await clusterGroup().findByRole('button', { name: 'Enter a cluster name' }));
    const input = clusterGroup().getByRole('textbox');
    expect(within(regionSelect()).getByRole('option', { name: 'us-west-2' })).toBeInTheDocument();
    await userEvent.type(input, 'new-cluster');

    expect(component.cluster).toBe('new-cluster');
    expect(component.moniker).toBeUndefined();
  });

  it('clears cluster text and recomputes regions when toggled back to cluster select', async () => {
    const component = {
      cloudProviderType: 'aws',
      credentials: 'test',
      region: 'us-east-1',
      cluster: 'typed',
    } as any;
    render(
      <AccountRegionClusterSelector
        accounts={[]}
        application={applicationWithServerGroups([
          { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
        ])}
        component={component}
        singleRegion={true as any}
      />,
    );

    await userEvent.click(await clusterGroup().findByRole('button', { name: 'Select an existing cluster' }));

    expect(component.cluster).toBeUndefined();
    expect(clusterGroup().getByRole('combobox')).toBeInTheDocument();
    expect(within(regionSelect()).queryByRole('option', { name: 'us-west-2' })).not.toBeInTheDocument();
  });

  it('reopens persisted custom cluster values in text input mode', async () => {
    render(
      <AccountRegionClusterSelector
        accounts={[]}
        application={applicationWithServerGroups([
          { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
        ])}
        component={{
          cloudProviderType: 'aws',
          credentials: 'test',
          region: 'us-east-1',
          cluster: 'persisted-custom-cluster',
        }}
        singleRegion={true as any}
      />,
    );

    expect(await clusterGroup().findByRole('textbox')).toHaveValue('persisted-custom-cluster');
    expect(clusterGroup().queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('shows all provider regions when no cluster is selected', async () => {
    render(
      <AccountRegionClusterSelector
        accounts={[]}
        application={applicationWithServerGroups([
          { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
        ])}
        component={{ cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' }}
        singleRegion={true as any}
      />,
    );

    expect(await within(regionSelect()).findByRole('option', { name: 'us-west-2' })).toBeInTheDocument();
    expect(clusterGroup().getByRole('combobox')).toBeInTheDocument();
  });
});

function applicationWithServerGroups(serverGroups: any[]) {
  return { getDataSource: () => ({ data: serverGroups }) } as any;
}
