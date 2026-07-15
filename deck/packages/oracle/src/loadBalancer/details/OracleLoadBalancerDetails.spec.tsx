import { mount } from 'enzyme';
import React from 'react';
import { BehaviorSubject } from 'rxjs';

import { useOracleLoadBalancerDetails } from './OracleLoadBalancerDetails';

describe('useOracleLoadBalancerDetails', () => {
  function renderHook(status: any, appOverrides: any = {}) {
    let result: any;
    const dataSource = {
      status$: new BehaviorSubject(status),
      refresh: jasmine.createSpy('refresh'),
    };
    const app = {
      getDataSource: jasmine.createSpy('getDataSource').and.returnValue(dataSource),
      ...appOverrides,
    };
    const props = {
      app,
      loadBalancerParams: { name: 'my-lb', region: 'us-phoenix-1', accountId: 'oracle-account' },
      autoClose: jasmine.createSpy('autoClose'),
    };

    function TestComponent() {
      result = useOracleLoadBalancerDetails(props);
      return null;
    }

    const component = mount(<TestComponent />);
    return { result, component, dataSource, props };
  }

  it('waits for load balancers to load before reporting not found', () => {
    const { result, props } = renderHook({ status: 'NOT_INITIALIZED', loaded: false, data: [], lastRefresh: 0 });

    expect(result.loading).toBe(true);
    expect(result.error).toBeUndefined();
    expect(props.autoClose).not.toHaveBeenCalled();
  });

  it('closes after loaded load balancers do not contain the requested load balancer', () => {
    const { result, props } = renderHook({ status: 'FETCHED', loaded: true, data: [], lastRefresh: 1 });

    expect(result.loading).toBe(false);
    expect(result.error).toBe('Load balancer not found');
    expect(props.autoClose).toHaveBeenCalled();
  });
});
