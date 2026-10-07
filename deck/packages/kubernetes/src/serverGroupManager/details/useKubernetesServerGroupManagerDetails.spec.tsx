import { act, waitFor } from '@testing-library/react';
import type { Mock } from 'vitest';

import { ManifestReader } from '@spinnaker/core';

import { useKubernetesServerGroupManagerDetails } from './useKubernetesServerGroupManagerDetails';
import type { IKubernetesServerGroupManagerDetailsProps } from './ServerGroupManagerDetails';
import { renderHookHarness } from '../../../../core/src/utils/testUtils/hookHarness';

describe('useKubernetesServerGroupManagerDetails', () => {
  beforeEach(() => {
    vi.spyOn(ManifestReader, 'getManifest').mockResolvedValue(manifestDetails() as any);
  });

  it('waits for the server group manager data source before loading manifest details', async () => {
    const ready = deferred<void>();
    const autoClose = vi.fn();
    const dataSource = {
      data: [] as any[],
      ready: vi.fn().mockReturnValue(ready.promise),
    };
    const rendered = renderDetailsHook(props(dataSource), autoClose);

    expect(dataSource.ready).toHaveBeenCalled();
    expect(autoClose).not.toHaveBeenCalled();
    expect(ManifestReader.getManifest).not.toHaveBeenCalled();
    expect(rendered.result.current[2]).toBe(true);

    dataSource.data = [serverGroupManagerDetails()];
    await act(async () => ready.resolve());

    await waitFor(() => expect(rendered.result.current[2]).toBe(false));
    expect(ManifestReader.getManifest).toHaveBeenCalledWith('k8s-local', 'dev', 'deployment backend');
    expect(rendered.result.current[0]?.displayName).toBe('backend');
    expect(rendered.result.current[1]?.name).toBe('deployment backend');
  });

  it('auto-closes when manifest details fail to load', async () => {
    (ManifestReader.getManifest as Mock).mockRejectedValue(new Error('manifest failed'));
    vi.spyOn(console, 'error').mockReturnValue(undefined);
    const autoClose = vi.fn();
    const dataSource = {
      data: [serverGroupManagerDetails()],
      ready: vi.fn().mockResolvedValue(undefined),
    };

    renderDetailsHook(props(dataSource), autoClose);

    await waitFor(() => expect(autoClose).toHaveBeenCalled());
  });

  it('loads details again when the requested manager changes', async () => {
    const autoClose = vi.fn();
    const dataSource = {
      data: [
        serverGroupManagerDetails(),
        serverGroupManagerDetails({ displayName: 'frontend', name: 'deployment frontend' }),
      ],
      ready: vi.fn().mockResolvedValue(undefined),
    };
    (ManifestReader.getManifest as Mock).mockImplementation((_account: string, _region: string, name: string) =>
      Promise.resolve(manifestDetails({ name })),
    );
    const rendered = renderDetailsHook(props(dataSource), autoClose);
    await waitFor(() => expect(rendered.result.current[0]?.displayName).toBe('backend'));

    rendered.rerenderHook({
      hookProps: props(dataSource, { name: 'deployment frontend' }),
      autoClose,
    });

    await waitFor(() => expect(rendered.result.current[0]?.displayName).toBe('frontend'));
    expect(ManifestReader.getManifest).toHaveBeenLastCalledWith('k8s-local', 'dev', 'deployment frontend');
    expect(rendered.result.current[1]?.name).toBe('deployment frontend');
  });

  it('cancels pending work when unmounted', async () => {
    const ready = deferred<void>();
    const autoClose = vi.fn();
    const dataSource = {
      data: [] as any[],
      ready: vi.fn().mockReturnValue(ready.promise),
    };
    const rendered = renderDetailsHook(props(dataSource), autoClose);

    rendered.unmount();
    dataSource.data = [serverGroupManagerDetails()];
    await act(async () => ready.resolve());

    expect(ManifestReader.getManifest).not.toHaveBeenCalled();
    expect(autoClose).not.toHaveBeenCalled();
  });
});

function renderDetailsHook(hookProps: IKubernetesServerGroupManagerDetailsProps, autoClose: () => void) {
  return renderHookHarness(
    ({ hookProps: currentProps, autoClose: currentAutoClose }) =>
      useKubernetesServerGroupManagerDetails(currentProps, currentAutoClose),
    { hookProps, autoClose },
  );
}

function props(dataSource: any, overrides: Record<string, string> = {}): IKubernetesServerGroupManagerDetailsProps {
  return {
    app: {
      getDataSource: () => dataSource,
    } as any,
    serverGroupManager: {
      accountId: 'k8s-local',
      provider: 'kubernetes',
      region: 'dev',
      name: 'deployment backend',
      ...overrides,
    },
  } as IKubernetesServerGroupManagerDetailsProps;
}

function serverGroupManagerDetails(overrides: Record<string, string> = {}) {
  return {
    account: 'k8s-local',
    cloudProvider: 'kubernetes',
    displayName: 'backend',
    name: 'deployment backend',
    region: 'dev',
    ...overrides,
  } as any;
}

function manifestDetails(overrides: Record<string, string> = {}) {
  return {
    account: 'k8s-local',
    location: 'dev',
    name: 'deployment backend',
    ...overrides,
  } as any;
}

function deferred<T>() {
  let resolve!: (value?: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
