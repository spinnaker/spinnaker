import { act, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import {
  filterClusterOptions,
  getAvailableClusters,
  makeClusterFilterKey,
  OnDemandClusterPicker,
} from './OnDemandClusterPicker';
import { AccountService } from '../../account';
import type { Application } from '../../application';
import { ApplicationDataSource } from '../../application/service/applicationDataSource';
import { FilterModelService } from '../../filterModel';
import { ClusterState, initialize } from '../../state';

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('OnDemandClusterPicker', () => {
  const clusters = [
    { account: 'prod', name: 'api' },
    { account: 'test', name: 'api' },
    { account: 'prod', name: 'web' },
  ] as any;

  beforeEach(() => {
    initialize();
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
  });

  it('builds stable account-qualified filter keys', () => {
    expect(makeClusterFilterKey(clusters[0])).toBe('prod:api');
  });

  it('omits selected clusters and preserves account/name option data', () => {
    expect(getAvailableClusters(clusters, { 'prod:api': true })).toEqual([
      { account: 'test', label: 'test api', name: 'api', value: 'test:api' },
      { account: 'prod', label: 'prod web', name: 'web', value: 'prod:web' },
    ]);
  });

  it('filters by account or cluster name case-insensitively and limits results', () => {
    const options = getAvailableClusters(clusters);
    expect(filterClusterOptions(options, 'PROD')).toHaveLength(2);
    expect(filterClusterOptions(options, 'web')).toEqual([options[2]]);
    expect(filterClusterOptions(options, '', 2)).toHaveLength(2);
  });

  it('updates the available count after a data-source refresh', () => {
    let refreshListener = () => undefined;
    const disposeRefreshListener = vi.fn();
    const dataSource = {
      clusters: clusters.slice(0, 2),
      onRefresh: (listener: () => void) => {
        refreshListener = listener;
        return disposeRefreshListener;
      },
      refresh: vi.fn(),
    } as any;
    const application = { getDataSource: () => dataSource } as any;
    const { unmount } = render(<OnDemandClusterPicker application={application} />);
    expect(screen.getByText('2 clusters found in this application')).toBeInTheDocument();

    dataSource.clusters = clusters;
    act(() => refreshListener());
    expect(screen.getByText('3 clusters found in this application')).toBeInTheDocument();

    unmount();
    expect(disposeRefreshListener).toHaveBeenCalledTimes(1);
  });

  it('selects a cluster through the visible search control', () => {
    const dataSource = {
      clusters,
      onRefresh: () => vi.fn(),
      refresh: vi.fn(),
    } as any;
    const applyParamsToUrl = vi
      .spyOn(ClusterState.filterModel.asFilterModel, 'applyParamsToUrl')
      .mockImplementation(() => undefined);
    render(<OnDemandClusterPicker application={{ getDataSource: () => dataSource } as any} />);
    const input = screen.getByRole('combobox', { name: 'Cluster' });

    fireEvent.mouseDown(input);
    fireEvent.mouseDown(screen.getByRole('option', { name: /prod api/i }));

    expect(ClusterState.filterModel.asFilterModel.sortFilter.clusters['prod:api']).toBe(true);
    expect(applyParamsToUrl).toHaveBeenCalled();
    expect(dataSource.refresh).toHaveBeenCalledWith(true);
  });

  it('force refreshes the complete latest selection while an earlier refresh is pending', async () => {
    const requests: string[] = [];
    const responses = [deferred<unknown[]>(), deferred<unknown[]>()];
    const application = {} as Application;
    const serverGroups = new ApplicationDataSource<unknown[]>(
      {
        key: 'serverGroups',
        defaultData: [],
        loader: () => {
          requests.push(
            FilterModelService.getCheckValues(ClusterState.filterModel.asFilterModel.sortFilter.clusters).join(),
          );
          return responses[requests.length - 1].promise;
        },
        onLoad: (_app, result) => Promise.resolve(result),
      },
      application,
    );
    serverGroups.clusters = [
      { account: 'prod', name: 'payments' },
      { account: 'staging', name: 'ledger' },
    ] as any;
    application.getDataSource = (key: string) => (key === 'serverGroups' ? serverGroups : undefined);
    vi.spyOn(ClusterState.filterModel.asFilterModel, 'applyParamsToUrl').mockImplementation(() => undefined);
    const refresh = vi.spyOn(serverGroups, 'refresh');
    const rendered = render(<OnDemandClusterPicker application={application} />);
    await flushPromises();

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Cluster' }));
    fireEvent.mouseDown(screen.getByRole('option', { name: 'prod payments' }));
    await flushPromises();
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Cluster' }));
    fireEvent.mouseDown(screen.getByRole('option', { name: 'staging ledger' }));
    await flushPromises();

    expect(requests).toEqual(['prod:payments', 'prod:payments,staging:ledger']);
    expect(refresh.mock.calls).toEqual([[true], [true]]);

    responses[1].resolve([]);
    responses[0].resolve([]);
    await flushPromises();
    rendered.unmount();
    serverGroups.destroy();
  });

  it('keeps block layout and matching finite select-menu height limits', () => {
    const root = document.createElement('div');
    const outerMenu = document.createElement('div');
    const menu = document.createElement('div');
    root.className = 'on-demand-cluster-picker';
    outerMenu.className = 'Select-menu-outer';
    menu.className = 'Select-menu';
    outerMenu.appendChild(menu);
    root.appendChild(outerMenu);
    document.body.appendChild(root);

    expect(window.getComputedStyle(root).display).toBe('block');
    expect(window.getComputedStyle(menu).maxHeight).toBe(window.getComputedStyle(outerMenu).maxHeight);
    expect(window.getComputedStyle(menu).maxHeight).not.toBe('none');

    root.remove();
  });
});
