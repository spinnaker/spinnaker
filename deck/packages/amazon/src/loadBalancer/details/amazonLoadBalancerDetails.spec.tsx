import { mount as enzymeMount, ReactWrapper } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';
import { BehaviorSubject } from 'rxjs';

import { DeckRuntimeContext } from '@spinnaker/core';

import { useAmazonLoadBalancerDetails } from './amazonLoadBalancerDetails';
import { RequestBuilder } from '../../../../core/src/api/ApiService';

describe('useAmazonLoadBalancerDetails', () => {
  const flush = () => new Promise((resolve) => setTimeout(resolve, 0));
  const defaultHttpClient = RequestBuilder.defaultHttpClient;
  let wrapper: ReactWrapper | undefined;
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {};
  });

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount();
      wrapper = undefined;
    }
    RequestBuilder.defaultHttpClient = defaultHttpClient;
  });

  it('does not refetch details when an unrelated state update re-renders with a new autoClose callback', async () => {
    const loadBalancer = {
      account: 'test',
      name: 'frontend',
      provider: 'aws',
      region: 'us-east-1',
      loadBalancerType: 'classic',
      subnets: [],
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [loadBalancer],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    const get = vi.fn().mockReturnValue(new Promise(() => undefined));
    RequestBuilder.defaultHttpClient = { get } as any;
    runtimeServices.securityGroupReader = {
      getApplicationSecurityGroup: vi.fn(),
    };

    function TestComponent() {
      useAmazonLoadBalancerDetails({
        app,
        loadBalancerParams: { accountId: 'test', name: 'frontend', provider: 'aws', region: 'us-east-1' },
        autoClose: () => undefined,
      } as any);
      return null;
    }

    await act(async () => {
      wrapper = mount(<TestComponent />);
      await flush();
    });
    wrapper.update();

    await act(async () => {
      await flush();
    });
    wrapper.update();

    expect(get).toHaveBeenCalledTimes(1);
  });

  it('does not update state after unmount when details loading finishes', async () => {
    const loadBalancer = {
      account: 'test',
      name: 'frontend',
      provider: 'aws',
      region: 'us-east-1',
      loadBalancerType: 'classic',
      subnets: [],
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [loadBalancer],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    let resolveDetails: (details: any[]) => void = () => undefined;
    const detailsRequest = new Promise<any[]>((resolve) => {
      resolveDetails = resolve;
    });
    const get = vi.fn().mockReturnValue(detailsRequest);
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    RequestBuilder.defaultHttpClient = { get } as any;
    runtimeServices.securityGroupReader = {
      getApplicationSecurityGroup: vi.fn(),
    };

    function TestComponent() {
      useAmazonLoadBalancerDetails({
        app,
        loadBalancerParams: { accountId: 'test', name: 'frontend', provider: 'aws', region: 'us-east-1' },
        autoClose: () => undefined,
      } as any);
      return null;
    }

    await act(async () => {
      wrapper = mount(<TestComponent />);
      await flush();
    });
    wrapper.unmount();
    wrapper = undefined;

    await act(async () => {
      resolveDetails([]);
      await flush();
    });

    const unmountedStateUpdateWarning = consoleError.mock.calls.some(([message]) =>
      String(message).includes('unmounted component'),
    );
    expect(unmountedStateUpdateWarning).toBe(false);
  });
});
