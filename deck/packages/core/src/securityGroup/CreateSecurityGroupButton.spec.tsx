import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import { SETTINGS } from '../config/settings';
import { CreateSecurityGroupButton } from './CreateSecurityGroupButton';

interface IDeferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
}

function deferred<T>(): IDeferred<T> {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

describe('<CreateSecurityGroupButton />', () => {
  const runtimeServices = {} as any;

  beforeEach(() => {
    SETTINGS.providers.buttonTestProvider = {
      defaults: {
        account: 'button-test-account',
        region: 'dev',
      },
    };
  });

  afterEach(SETTINGS.resetToOriginal);

  it('opens a React security group modal after selecting a provider from a React click handler', async () => {
    const providerSelection = deferred<string>();
    const modalShown = deferred<void>();
    const modal = {
      show: vi.fn().mockImplementation(() => modalShown.resolve(undefined)),
    };
    const app = {
      defaultCredentials: {},
      defaultRegions: {},
    } as any;
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockReturnValue(providerSelection.promise);
    vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue({
      CreateSecurityGroupModal: modal,
    });

    const wrapper = mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <CreateSecurityGroupButton app={app} />
      </DeckRuntimeContext.Provider>,
    );
    act(() => {
      wrapper.find('button').simulate('click');
    });
    expect(modal.show).not.toHaveBeenCalled();

    await act(async () => {
      providerSelection.resolve('buttonTestProvider');
      await modalShown.promise;
    });

    expect(modal.show).toHaveBeenCalledWith(
      {
        application: app,
        credentials: 'button-test-account',
        isNew: true,
        region: 'dev',
      },
      runtimeServices,
    );
  });

  it('opens a React security group modal for providers without configured defaults', async () => {
    const providerSelection = deferred<string>();
    const modalShown = deferred<void>();
    const modal = {
      show: vi.fn().mockImplementation(() => modalShown.resolve(undefined)),
    };
    const app = {
      defaultCredentials: {},
      defaultRegions: {},
    } as any;
    SETTINGS.providers.kubernetes = {};
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockReturnValue(providerSelection.promise);
    vi.spyOn(CloudProviderRegistry, 'getValue').mockReturnValue({
      CreateSecurityGroupModal: modal,
    });

    const wrapper = mount(
      <DeckRuntimeContext.Provider value={{ services: runtimeServices }}>
        <CreateSecurityGroupButton app={app} />
      </DeckRuntimeContext.Provider>,
    );
    act(() => {
      wrapper.find('button').simulate('click');
    });
    expect(modal.show).not.toHaveBeenCalled();

    await act(async () => {
      providerSelection.resolve('kubernetes');
      await modalShown.promise;
    });

    expect(modal.show).toHaveBeenCalledWith(
      {
        application: app,
        credentials: undefined,
        isNew: true,
        region: undefined,
      },
      runtimeServices,
    );
  });
});
