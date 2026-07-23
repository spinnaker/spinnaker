import { mount, shallow } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import {
  filterClusterOptions,
  getAvailableClusters,
  makeClusterFilterKey,
  OnDemandClusterPicker,
} from './OnDemandClusterPicker';
import { onDemandClusterPickerComponent } from './onDemandClusterPicker.component';
import { AccountTag } from '../../account';
import type { Application } from '../../application';
import { ApplicationDataSource } from '../../application/service/applicationDataSource';
import { FilterModelService } from '../../filterModel';
import { ReactSelectInput } from '../../presentation';
import { ClusterState, initialize } from '../../state';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0));

class TestServerGroupsDataSource {
  public fetchOnDemand = true;
  public refresh = jasmine.createSpy('refresh');
  public callbacks: Array<() => void> = [];

  constructor(public clusters: Array<{ account: string; name: string }> = []) {}

  public onRefresh(_scope: unknown, callback: () => void): () => void {
    this.callbacks.push(callback);
    return () => {
      this.callbacks = this.callbacks.filter((candidate) => candidate !== callback);
    };
  }

  public emit(): void {
    this.callbacks.forEach((callback) => callback());
  }
}

const makeApplication = (serverGroups: TestServerGroupsDataSource) =>
  ({
    getDataSource: (key: string) => (key === 'serverGroups' ? serverGroups : undefined),
  } as any);

describe('OnDemandClusterPicker', () => {
  beforeEach(() => initialize());

  it('uses the exact account-qualified cluster key', () => {
    expect(makeClusterFilterKey({ account: 'prod', name: 'payments' })).toBe('prod:payments');
  });

  it('keeps duplicate names from different accounts and excludes only truthy selections', () => {
    const clusters = [
      { account: 'prod', name: 'payments' },
      { account: 'staging', name: 'payments' },
      { account: 'prod', name: 'ledger' },
    ];

    expect(getAvailableClusters(clusters, { 'prod:payments': true, 'staging:payments': false })).toEqual([
      jasmine.objectContaining({ value: 'staging:payments', account: 'staging', name: 'payments' }),
      jasmine.objectContaining({ value: 'prod:ledger', account: 'prod', name: 'ledger' }),
    ]);
    expect(getAvailableClusters(undefined, undefined)).toEqual([]);
  });

  it('filters account and name case-insensitively and caps results at 50 by default', () => {
    const options = getAvailableClusters(
      Array.from({ length: 60 }, (_, index) => ({
        account: index === 55 ? 'Main-PROD' : 'staging',
        name: index === 55 ? 'Payment-API' : `cluster-${index}`,
      })),
      {},
    );

    expect(filterClusterOptions(options, '')).toHaveSize(50);
    expect(filterClusterOptions(options, 'PROD payment')).toEqual([
      jasmine.objectContaining({ account: 'Main-PROD', name: 'Payment-API' }),
    ]);
    expect(filterClusterOptions(undefined, 'anything')).toEqual([]);
  });

  it('renders the legacy copy and account-qualified plain select options', () => {
    const serverGroups = new TestServerGroupsDataSource([
      { account: 'prod', name: 'payments' },
      { account: 'staging', name: 'ledger' },
    ]);
    const wrapper = mount(<OnDemandClusterPicker application={makeApplication(serverGroups)} />);
    const select = wrapper.find(ReactSelectInput);

    expect(wrapper.find('h4').text()).toBe('2 clusters found in this application');
    expect(wrapper.text()).toContain('Not all clusters are shown. Select or enter a cluster name below to view:');
    expect(select.prop('mode')).toBe('PLAIN');
    expect(select.prop('placeholder')).toBe('Enter cluster name here');
    expect(select.prop('value')).toBeNull();

    const option = (select.prop('options') as any[])[0];
    const renderedOption = shallow(<div>{(select.prop('optionRenderer') as any)(option)}</div>);
    expect(renderedOption.find(AccountTag).prop('account')).toBe('prod');
    expect(renderedOption.text()).toContain('payments');

    wrapper.unmount();
  });

  it('selects the full key, updates the URL before refresh, clears control state, and ignores null', () => {
    const serverGroups = new TestServerGroupsDataSource([{ account: 'prod', name: 'payments' }]);
    const application = makeApplication(serverGroups);
    const applyParamsToUrl = spyOn(ClusterState.filterModel.asFilterModel, 'applyParamsToUrl');
    const wrapper = mount(<OnDemandClusterPicker application={application} />);
    const onChange = wrapper.find(ReactSelectInput).prop('onChange') as any;

    act(() => onChange({ target: { value: null } }));
    expect(serverGroups.refresh).not.toHaveBeenCalled();

    act(() => onChange({ target: { value: 'prod:payments' } }));
    wrapper.update();

    expect(ClusterState.filterModel.asFilterModel.sortFilter.clusters).toEqual({ 'prod:payments': true });
    expect(applyParamsToUrl).toHaveBeenCalledBefore(serverGroups.refresh);
    expect(serverGroups.refresh).toHaveBeenCalledTimes(1);
    expect(wrapper.find(ReactSelectInput).prop('value')).toBeNull();
    expect(wrapper.find(ReactSelectInput).prop('options')).toEqual([]);

    wrapper.unmount();
  });

  it('force refreshes the latest full selection while the previous refresh is loading', async () => {
    const firstKey = 'prod:payments';
    const secondKey = 'staging:ledger';
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
    ];
    application.getDataSource = (key: string) => (key === 'serverGroups' ? serverGroups : undefined);
    const applyParamsToUrl = spyOn(ClusterState.filterModel.asFilterModel, 'applyParamsToUrl');
    const refresh = spyOn(serverGroups, 'refresh').and.callThrough();
    const wrapper = mount(<OnDemandClusterPicker application={application} />);
    const onChange = wrapper.find(ReactSelectInput).prop('onChange') as any;
    await flushPromises();

    act(() => onChange({ target: { value: firstKey } }));
    await flushPromises();
    act(() => onChange({ target: { value: secondKey } }));
    await flushPromises();

    expect(requests).toEqual([firstKey, `${firstKey},${secondKey}`]);
    expect(refresh.calls.allArgs()).toEqual([[true], [true]]);
    expect(applyParamsToUrl.calls.all()[0].invocationOrder).toBeLessThan(refresh.calls.all()[0].invocationOrder);
    expect(applyParamsToUrl.calls.all()[1].invocationOrder).toBeLessThan(refresh.calls.all()[1].invocationOrder);

    responses[1].resolve([]);
    responses[0].resolve([]);
    await flushPromises();
    wrapper.unmount();
    serverGroups.destroy();
  });

  it('recomputes total and available summaries after refresh and cleans up its subscription', () => {
    const serverGroups = new TestServerGroupsDataSource([{ account: 'prod', name: 'payments' }]);
    ClusterState.filterModel.asFilterModel.sortFilter.clusters = { 'prod:payments': true };
    const wrapper = mount(<OnDemandClusterPicker application={makeApplication(serverGroups)} />);

    expect(serverGroups.callbacks).toHaveSize(1);
    expect(wrapper.find('h4').text()).toBe('1 clusters found in this application');
    expect(wrapper.find(ReactSelectInput).prop('options')).toEqual([]);

    serverGroups.clusters = [
      { account: 'prod', name: 'payments' },
      { account: 'staging', name: 'payments' },
    ];
    act(() => serverGroups.emit());
    wrapper.update();

    expect(wrapper.find('h4').text()).toBe('2 clusters found in this application');
    expect(wrapper.find(ReactSelectInput).prop('options')).toEqual([
      jasmine.objectContaining({ value: 'staging:payments' }),
    ]);

    wrapper.unmount();
    expect(serverGroups.callbacks).toHaveSize(0);
  });

  it('styles the Angular host as a block and uses consistent select menu height limits', () => {
    const host = document.createElement('on-demand-cluster-picker');
    const root = document.createElement('div');
    const outerMenu = document.createElement('div');
    const menu = document.createElement('div');
    root.className = 'on-demand-cluster-picker';
    outerMenu.className = 'Select-menu-outer';
    menu.className = 'Select-menu';
    outerMenu.appendChild(menu);
    root.appendChild(outerMenu);
    host.appendChild(root);
    document.body.appendChild(host);

    expect(window.getComputedStyle(host).display).toBe('block');
    expect(window.getComputedStyle(menu).maxHeight).toBe(window.getComputedStyle(outerMenu).maxHeight);
    expect(window.getComputedStyle(menu).maxHeight).not.toBe('none');

    host.remove();
  });

  it('exposes only the application binding through the Angular compatibility wrapper', () => {
    expect(onDemandClusterPickerComponent.bindings).toEqual({ application: '<' });
  });
});
