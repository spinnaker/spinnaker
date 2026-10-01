import { act } from '@testing-library/react';

import { useClusterMasterState } from './ClusterMaster';
import { ClusterState, initialize } from '../state';
import { renderHookHarness } from '../utils/testUtils/hookHarness';

describe('useClusterMasterState', () => {
  beforeEach(() => initialize());

  const createApp = () => {
    let resolve!: () => void;
    let reject!: (error: Error) => void;
    const promise = new Promise<void>((promiseResolve, promiseReject) => {
      resolve = promiseResolve;
      reject = promiseReject;
    });
    const ready = { promise, resolve, reject };
    let refreshListener: () => void = () => undefined;
    const unsubscribe = vi.fn();
    const app = {
      serverGroups: {
        ready: () => ready.promise,
        onRefresh: (listener: () => void) => {
          refreshListener = listener;
          return unsubscribe;
        },
      },
      setActiveState: vi.fn(),
    } as any;
    return { app, ready, refresh: () => refreshListener(), unsubscribe };
  };

  it('activates filtering, updates groups after readiness and refresh, then cleans up', async () => {
    const fixture = createApp();
    const updateClusterGroups = vi.fn();
    const activate = vi.fn();
    const clearAll = vi.fn();
    ClusterState.filterService = { updateClusterGroups } as any;
    ClusterState.filterModel = { activate } as any;
    ClusterState.multiselectModel = { clearAll } as any;
    const harness = renderHookHarness(({ app }) => useClusterMasterState(app), { app: fixture.app });

    expect(harness.result.current).toEqual({ initialized: false, loadError: false });
    expect(fixture.app.setActiveState).toHaveBeenCalledWith(fixture.app.serverGroups);
    expect(activate).toHaveBeenCalled();

    await act(async () => fixture.ready.resolve());
    expect(harness.result.current).toEqual({ initialized: true, loadError: false });
    expect(updateClusterGroups).toHaveBeenCalledWith(fixture.app);

    act(() => fixture.refresh());
    expect(updateClusterGroups).toHaveBeenCalledTimes(2);

    harness.unmount();
    expect(fixture.unsubscribe).toHaveBeenCalled();
    expect(fixture.app.setActiveState).toHaveBeenLastCalledWith();
    expect(clearAll).toHaveBeenCalled();
  });

  it('reports a ready failure without updating groups', async () => {
    const fixture = createApp();
    const updateClusterGroups = vi.fn();
    ClusterState.filterService = { updateClusterGroups } as any;
    ClusterState.filterModel = { activate: vi.fn() } as any;
    ClusterState.multiselectModel = { clearAll: vi.fn() } as any;
    const harness = renderHookHarness(({ app }) => useClusterMasterState(app), { app: fixture.app });

    await act(async () => fixture.ready.reject(new Error('load failed')));

    expect(harness.result.current).toEqual({ initialized: true, loadError: true });
    expect(updateClusterGroups).not.toHaveBeenCalled();
    harness.unmount();
  });

  it('ignores late readiness resolution, rejection, and refresh callbacks after unmount', async () => {
    const updateClusterGroups = vi.fn();
    ClusterState.filterService = { updateClusterGroups } as any;
    ClusterState.filterModel = { activate: vi.fn() } as any;
    ClusterState.multiselectModel = { clearAll: vi.fn() } as any;
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const resolving = createApp();
    const resolvingHarness = renderHookHarness(({ app }) => useClusterMasterState(app), { app: resolving.app });
    resolvingHarness.unmount();
    consoleError.mockClear();
    await act(async () => resolving.ready.resolve());
    act(() => resolving.refresh());

    const rejecting = createApp();
    const rejectingHarness = renderHookHarness(({ app }) => useClusterMasterState(app), { app: rejecting.app });
    rejectingHarness.unmount();
    await act(async () => rejecting.ready.reject(new Error('late failure')));
    act(() => rejecting.refresh());

    expect(updateClusterGroups).not.toHaveBeenCalled();
    expect(resolving.unsubscribe).toHaveBeenCalledTimes(1);
    expect(rejecting.unsubscribe).toHaveBeenCalledTimes(1);
    expect(consoleError).not.toHaveBeenCalled();
  });
});
