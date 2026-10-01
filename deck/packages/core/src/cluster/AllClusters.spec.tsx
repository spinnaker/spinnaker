import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { AllClusters, ClusterControls, CreateServerGroupButton, hasReactCloneServerGroupModal } from './AllClusters';
import { customBannersByName } from '../banner/customBannersByName';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import { SETTINGS } from '../config/settings';
import { ClusterState, initialize } from '../state';
import { renderWithRouter } from '../utils/testUtils/rtl';

describe('AllClusters controls', () => {
  beforeEach(() => initialize());

  it('detects providers with a React clone server group modal', () => {
    expect(hasReactCloneServerGroupModal({} as any, {} as any, { serverGroup: {} } as any)).toBe(false);
    expect(
      hasReactCloneServerGroupModal(
        {} as any,
        {} as any,
        { serverGroup: { CloneServerGroupModal: { show: vi.fn() } } } as any,
      ),
    ).toBe(true);
  });

  it('updates multiselect and instance display settings through visible controls', () => {
    const updateClusterGroups = vi.fn();
    const syncNavigation = vi
      .spyOn(ClusterState.multiselectModel, 'syncNavigation')
      .mockImplementation(() => undefined);
    const sortFilter = { multiselect: false, showAllInstances: false, listInstances: false } as any;
    const { rerender } = render(
      <ClusterControls showInstancesToggle={true} sortFilter={sortFilter} updateClusterGroups={updateClusterGroups} />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Edit multiple server groups' }));
    expect(ClusterState.filterModel.asFilterModel.sortFilter.multiselect).toBe(true);
    expect(syncNavigation).toHaveBeenCalled();

    fireEvent.click(screen.getByRole('checkbox', { name: 'Instances' }));
    expect(ClusterState.filterModel.asFilterModel.sortFilter.showAllInstances).toBe(true);
    expect(ClusterState.filterModel.asFilterModel.sortFilter.listInstances).toBe(false);

    rerender(
      <ClusterControls
        showInstancesToggle={true}
        sortFilter={{ ...sortFilter, showAllInstances: true }}
        updateClusterGroups={updateClusterGroups}
      />,
    );
    fireEvent.click(screen.getByRole('checkbox', { name: 'with details' }));
    expect(ClusterState.filterModel.asFilterModel.sortFilter.listInstances).toBe(true);
    expect(updateClusterGroups).toHaveBeenCalledTimes(3);
  });

  it('builds and opens the selected provider modal', async () => {
    const app = {} as any;
    const command = { application: 'app' };
    const show = vi.fn();
    vi.spyOn(ProviderSelectionService, 'isDisabled').mockResolvedValue(false);
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockResolvedValue('test');
    vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue({ CloneServerGroupModal: { show } } as any);
    const buildNewServerGroupCommand = vi.fn().mockResolvedValue(command);
    const services = { serverGroupCommandBuilder: { buildNewServerGroupCommand } } as any;

    render(
      <DeckRuntimeContext.Provider value={{ services }}>
        <CreateServerGroupButton app={app} />
      </DeckRuntimeContext.Provider>,
    );
    fireEvent.click(await screen.findByRole('button', { name: /Create Server Group/ }));

    await waitFor(() => expect(show).toHaveBeenCalled());
    expect(buildNewServerGroupCommand).toHaveBeenCalledWith(app, 'test', null);
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Create New Server Group',
        application: app,
        command,
        provider: 'test',
        isNew: true,
      }),
      services,
    );
  });

  it('shows a warning when the selected provider has no clone modal', async () => {
    vi.spyOn(ProviderSelectionService, 'isDisabled').mockResolvedValue(false);
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockResolvedValue('test');
    vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue({} as any);
    const services = {
      serverGroupCommandBuilder: { buildNewServerGroupCommand: vi.fn().mockResolvedValue({}) },
    } as any;

    render(
      <DeckRuntimeContext.Provider value={{ services }}>
        <CreateServerGroupButton app={{} as any} />
      </DeckRuntimeContext.Provider>,
    );
    fireEvent.click(await screen.findByRole('button', { name: /Create Server Group/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No React clone server group modal is registered for provider "test".',
    );
  });
});

describe('AllClusters views', () => {
  const originalBanners = SETTINGS.banners;
  const originalSpinnakerSettings = (window as any).spinnakerSettings;
  const services = { serverGroupCommandBuilder: { buildNewServerGroupCommand: vi.fn() } } as any;

  const makeApplication = (fetchOnDemand: boolean) => {
    const serverGroups = {
      clusters: [{ account: 'prod', name: 'payments' }],
      data: [],
      fetchOnDemand,
      loadFailure: false,
      onRefresh: () => vi.fn(),
      refresh: vi.fn(),
    };
    return {
      name: 'payments',
      getDataSource: (key: string) => (key === 'serverGroups' ? serverGroups : undefined),
    } as any;
  };

  const renderView = (fetchOnDemand: boolean, initialized = true) =>
    renderWithRouter(
      <DeckRuntimeContext.Provider value={{ services }}>
        <AllClusters app={makeApplication(fetchOnDemand)} initialized={initialized} loadError={false} />
      </DeckRuntimeContext.Provider>,
    );

  beforeEach(() => {
    initialize();
    (window as any).spinnakerSettings = { feature: {} };
    SETTINGS.banners = [{ key: 'clusterTest', active: true, routes: [''] }];
    customBannersByName.clusterTest = () => <div>Cluster test banner</div>;
    vi.spyOn(ProviderSelectionService, 'isDisabled').mockResolvedValue(false);
  });

  afterEach(() => {
    SETTINGS.banners = originalBanners;
    (window as any).spinnakerSettings = originalSpinnakerSettings;
    delete customBannersByName.clusterTest;
  });

  it('renders one on-demand picker and suppresses filter tags without changing initialized content', async () => {
    const { container } = renderView(true);

    expect(screen.getAllByRole('combobox', { name: 'Cluster' })).toHaveLength(1);
    expect(container.querySelector('.filter-tags')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit multiple server groups' })).toBeInTheDocument();
    expect(screen.getByText('Cluster test banner')).toBeInTheDocument();
    expect(container.querySelector('.content .rollup')).toBeInTheDocument();
    expect(screen.queryByText('Loading ...')).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Create Server Group' })).toBeInTheDocument();
  });

  it('keeps filter tags and omits the picker during normal fetching', async () => {
    const { container } = renderView(false);

    expect(screen.queryByRole('combobox', { name: 'Cluster' })).not.toBeInTheDocument();
    expect(container.querySelector('.filter-tags')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit multiple server groups' })).toBeInTheDocument();
    expect(screen.getByText('Cluster test banner')).toBeInTheDocument();
    expect(container.querySelector('.content .rollup')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Create Server Group' })).toBeInTheDocument();
  });

  it('keeps the standard loading spinner and groupings while hiding initialized content', () => {
    const { container } = renderView(false, false);

    expect(screen.getByText('Loading ...')).toBeInTheDocument();
    expect(screen.queryByText('Cluster test banner')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Create Server Group' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Cluster' })).not.toBeInTheDocument();
    expect(container.querySelector('.content .rollup')).toBeInTheDocument();
  });
});
