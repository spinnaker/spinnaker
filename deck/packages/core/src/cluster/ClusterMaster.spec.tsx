import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import type { Application } from '../application';
import { ClusterState, initialize } from '../state';
import { useClusterMasterState } from './ClusterMaster';

interface IDeferred<T> {
  promise: Promise<T>;
  reject: (error: Error) => void;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  let reject: (error: Error) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

class TestServerGroupsDataSource {
  public callbacks: Array<() => void> = [];
  public onRefresh = jasmine.createSpy('onRefresh').and.callFake((_scope: unknown, callback: () => void) => {
    this.callbacks.push(callback);
    return () => {
      this.callbacks = this.callbacks.filter((candidate) => candidate !== callback);
    };
  });
  public ready = jasmine.createSpy('ready').and.callFake(() => this.readiness.promise);

  constructor(private readiness: IDeferred<unknown>) {}

  public emit(): void {
    this.callbacks.forEach((callback) => callback());
  }
}

function StateHarness({ app }: { app: Application }): JSX.Element {
  const state = useClusterMasterState(app);
  return <span>{`${state.initialized}:${state.loadError}`}</span>;
}

const makeApplication = (serverGroups: TestServerGroupsDataSource) =>
  (({
    serverGroups,
    setActiveState: jasmine.createSpy('setActiveState'),
  } as any) as Application);

describe('ClusterMaster lifecycle', () => {
  beforeEach(() => initialize());

  it('activates filters and server groups, subscribes before readiness, and updates groups initially and on refresh', async () => {
    const readiness = deferred<unknown>();
    const serverGroups = new TestServerGroupsDataSource(readiness);
    const app = makeApplication(serverGroups);
    const activate = spyOn(ClusterState.filterModel, 'activate');
    const updateClusterGroups = spyOn(ClusterState.filterService, 'updateClusterGroups');
    const clearAll = spyOn(ClusterState.multiselectModel, 'clearAll');
    const wrapper = mount(<StateHarness app={app} />);

    expect(app.setActiveState).toHaveBeenCalledWith(serverGroups as any);
    expect(activate).toHaveBeenCalledTimes(1);
    expect(serverGroups.onRefresh).toHaveBeenCalledBefore(serverGroups.ready);
    expect(wrapper.text()).toBe('false:false');

    await act(async () => readiness.resolve(undefined));
    wrapper.update();

    expect(wrapper.text()).toBe('true:false');
    expect(updateClusterGroups).toHaveBeenCalledTimes(1);

    act(() => serverGroups.emit());
    expect(updateClusterGroups).toHaveBeenCalledTimes(2);

    wrapper.unmount();
    expect(serverGroups.callbacks).toHaveSize(0);
    expect(app.setActiveState).toHaveBeenCalledWith();
    expect(clearAll).toHaveBeenCalledTimes(1);
  });

  it('initializes with a load error when server-group readiness rejects', async () => {
    const readiness = deferred<unknown>();
    const serverGroups = new TestServerGroupsDataSource(readiness);
    const wrapper = mount(<StateHarness app={makeApplication(serverGroups)} />);

    await act(async () => readiness.reject(new Error('load failed')));
    wrapper.update();

    expect(wrapper.text()).toBe('true:true');
    wrapper.unmount();
  });

  it('ignores late readiness resolution and rejection after unmount', async () => {
    const updateClusterGroups = spyOn(ClusterState.filterService, 'updateClusterGroups');
    const resolvingReadiness = deferred<unknown>();
    const resolvingWrapper = mount(
      <StateHarness app={makeApplication(new TestServerGroupsDataSource(resolvingReadiness))} />,
    );
    resolvingWrapper.unmount();

    await act(async () => resolvingReadiness.resolve(undefined));
    expect(updateClusterGroups).not.toHaveBeenCalled();

    const rejectingReadiness = deferred<unknown>();
    const rejectingWrapper = mount(
      <StateHarness app={makeApplication(new TestServerGroupsDataSource(rejectingReadiness))} />,
    );
    rejectingWrapper.unmount();

    await act(async () => rejectingReadiness.reject(new Error('late failure')));
    expect(updateClusterGroups).not.toHaveBeenCalled();
  });
});
