import { mount } from 'enzyme';
import React from 'react';

import { ManifestReader } from '@spinnaker/core';

import { useKubernetesServerGroupManagerDetails } from './useKubernetesServerGroupManagerDetails';
import type { IKubernetesServerGroupManagerDetailsProps } from './ServerGroupManagerDetails';

describe('useKubernetesServerGroupManagerDetails', () => {
  beforeEach(() => {
    spyOn(ManifestReader, 'getManifest').and.returnValue(Promise.resolve(manifestDetails()) as any);
  });

  it('waits for the server group manager data source before loading manifest details', async () => {
    let resolveReady: () => void;
    const ready = new Promise<void>((resolve) => {
      resolveReady = resolve;
    });
    const autoClose = jasmine.createSpy('autoClose');
    const dataSource = {
      data: [] as any[],
      ready: jasmine.createSpy('ready').and.returnValue(ready),
    };
    const component = mount(<HookHarness {...props(dataSource)} autoClose={autoClose} />);

    await settle();
    component.update();

    expect(dataSource.ready).toHaveBeenCalled();
    expect(autoClose).not.toHaveBeenCalled();
    expect(ManifestReader.getManifest).not.toHaveBeenCalled();
    expect(component.find('.hook-state').prop('data-loading')).toBe(true);

    dataSource.data = [serverGroupManagerDetails()];
    resolveReady!();
    await settle();
    await settle();
    component.update();

    expect(ManifestReader.getManifest).toHaveBeenCalledWith('k8s-local', 'dev', 'deployment backend');
    expect(component.find('.hook-state').prop('data-loading')).toBe(false);
    expect(component.find('.hook-state').prop('data-manager')).toBe('backend');
    expect(component.find('.hook-state').prop('data-manifest')).toBe('deployment backend');
  });

  it('auto-closes when manifest details fail to load', async () => {
    (ManifestReader.getManifest as jasmine.Spy).and.returnValue(Promise.reject(new Error('manifest failed')));
    spyOn(console, 'error').and.stub();
    const autoClose = jasmine.createSpy('autoClose');
    const dataSource = {
      data: [serverGroupManagerDetails()],
      ready: jasmine.createSpy('ready').and.returnValue(Promise.resolve()),
    };

    const component = mount(<HookHarness {...props(dataSource)} autoClose={autoClose} />);

    await settle();
    await settle();
    component.update();

    expect(autoClose).toHaveBeenCalled();

    component.unmount();
  });
});

function HookHarness({ autoClose, ...props }: IKubernetesServerGroupManagerDetailsProps & { autoClose: () => void }) {
  const [serverGroupManager, manifest, loading] = useKubernetesServerGroupManagerDetails(props, autoClose);

  return (
    <div
      className="hook-state"
      data-loading={loading}
      data-manager={serverGroupManager?.displayName || ''}
      data-manifest={manifest?.name || ''}
    />
  );
}

function props(dataSource: any): IKubernetesServerGroupManagerDetailsProps {
  return {
    app: {
      getDataSource: () => dataSource,
    } as any,
    serverGroupManager: {
      accountId: 'k8s-local',
      provider: 'kubernetes',
      region: 'dev',
      name: 'deployment backend',
    },
  } as IKubernetesServerGroupManagerDetailsProps;
}

function serverGroupManagerDetails() {
  return {
    account: 'k8s-local',
    cloudProvider: 'kubernetes',
    displayName: 'backend',
    name: 'deployment backend',
    region: 'dev',
  } as any;
}

function manifestDetails() {
  return {
    account: 'k8s-local',
    location: 'dev',
    name: 'deployment backend',
  } as any;
}

const settle = () => new Promise((resolve) => setTimeout(resolve));
