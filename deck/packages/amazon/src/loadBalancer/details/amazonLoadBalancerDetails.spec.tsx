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
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue({
        status$,
        refresh: jasmine.createSpy('refresh'),
      }),
    } as any;
    const get = jasmine.createSpy('get').and.returnValue(new Promise(() => undefined));
    RequestBuilder.defaultHttpClient = { get } as any;
    runtimeServices.securityGroupReader = {
      getApplicationSecurityGroup: jasmine.createSpy('getApplicationSecurityGroup'),
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
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue({
        status$,
        refresh: jasmine.createSpy('refresh'),
      }),
    } as any;
    let resolveDetails: (details: any[]) => void = () => undefined;
    const detailsRequest = new Promise<any[]>((resolve) => {
      resolveDetails = resolve;
    });
    const get = jasmine.createSpy('get').and.returnValue(detailsRequest);
    const consoleError = spyOn(console, 'error');
    RequestBuilder.defaultHttpClient = { get } as any;
    runtimeServices.securityGroupReader = {
      getApplicationSecurityGroup: jasmine.createSpy('getApplicationSecurityGroup'),
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

    const unmountedStateUpdateWarning = consoleError.calls
      .allArgs()
      .some(([message]) => String(message).includes('unmounted component'));
    expect(unmountedStateUpdateWarning).toBe(false);
  });
});
