import { mount } from 'enzyme';
import React from 'react';
import { BehaviorSubject } from 'rxjs';

import { ExecutionDetailsTasks } from '@spinnaker/core';

import {
  CloudrunEditLoadBalancerExecutionDetails,
  getLoadBalancerCompositeKey,
} from './CloudrunEditLoadBalancerExecutionDetails';
import {
  CloudrunEditLoadBalancerStageConfig,
  CloudrunLoadBalancerChoiceModal,
  getLoadBalancerOptionLabel,
} from './CloudrunEditLoadBalancerStageConfig';
import { CLOUDRUN_EDIT_LOAD_BALANCER_STAGE_CONFIG } from './cloudrunEditLoadBalancerStage';
import { CloudrunLoadBalancerModal } from '../../../loadBalancer/configure/wizard/CloudrunLoadBalancerModal';
import {
  findCloudrunLoadBalancer,
  useCloudrunLoadBalancerDetails,
} from '../../../loadBalancer/details/useCloudrunLoadBalancerDetails';

describe('Cloud Run edit load balancer stage', () => {
  function buildChoiceModal(overrides: any = {}) {
    const props = {
      application: {
        loadBalancers: {
          data: [],
          ready: vi.fn().mockReturnValue(Promise.resolve()),
        },
      },
      closeModal: vi.fn(),
      dismissModal: vi.fn(),
      ...overrides,
    } as any;

    return new CloudrunLoadBalancerChoiceModal(props);
  }

  it('uses a composite key for load balancer selection', () => {
    expect(getLoadBalancerCompositeKey({ name: 'default', account: 'test', region: 'us-central1' } as any)).toBe(
      'default:test:us-central1',
    );
    expect(getLoadBalancerCompositeKey({ name: 'default', account: 'prod', region: 'us-central1' } as any)).toBe(
      'default:prod:us-central1',
    );
  });

  it('matches load balancer details by region', () => {
    const loadBalancers = [
      { name: 'default', account: 'test', region: 'europe-west1' },
      { name: 'default', account: 'test', region: 'us-central1' },
    ] as any;

    expect(
      findCloudrunLoadBalancer(loadBalancers, {
        name: 'default',
        accountId: 'test',
        region: 'us-central1',
      } as any),
    ).toBe(loadBalancers[1]);
  });

  it('includes region in load balancer option labels', () => {
    expect(getLoadBalancerOptionLabel({ name: 'default', account: 'test', region: 'us-central1' } as any)).toBe(
      'test default us-central1',
    );
  });

  it('registers React execution details before task details', () => {
    expect(CLOUDRUN_EDIT_LOAD_BALANCER_STAGE_CONFIG.executionDetailsSections).toEqual([
      CloudrunEditLoadBalancerExecutionDetails,
      ExecutionDetailsTasks,
    ]);
  });

  it('stops loading when load balancers fail to load', async () => {
    const modal = buildChoiceModal({
      application: {
        loadBalancers: {
          data: [],
          ready: vi.fn().mockReturnValue(Promise.reject(new Error('load failed'))),
        },
      },
    });
    vi.spyOn(modal, 'setState').mockReturnValue(undefined);

    modal.componentDidMount();
    await Promise.resolve();
    await Promise.resolve();

    expect(modal.setState).toHaveBeenCalledWith({ loading: false });
  });

  it('does not update load balancer choices after unmount', async () => {
    let resolveReady: () => void;
    const ready = new Promise<void>((resolve) => {
      resolveReady = resolve;
    });
    const modal = buildChoiceModal({
      application: {
        loadBalancers: {
          data: [{ name: 'default', account: 'test', region: 'us-central1', cloudProvider: 'cloudrun' }],
          ready: vi.fn().mockReturnValue(ready),
        },
      },
    });
    vi.spyOn(modal, 'setState').mockReturnValue(undefined);

    modal.componentDidMount();
    (modal as any).componentWillUnmount?.();
    resolveReady!();
    await ready;
    await Promise.resolve();

    expect(modal.setState).not.toHaveBeenCalled();
  });

  it('handles dismissal of the load balancer edit modal', () => {
    const catchSpy = vi.fn();
    const modalResult = { then: vi.fn().mockReturnValue({ catch: catchSpy }) };
    vi.spyOn(CloudrunLoadBalancerModal, 'show').mockReturnValue(modalResult as any);
    const modal = buildChoiceModal();
    modal.state = { ...modal.state, selectedLoadBalancer: { name: 'default' } as any };

    (modal as any).submit();

    expect(catchSpy).toHaveBeenCalled();
  });

  it('handles dismissal of the load balancer choice modal when adding to a stage', () => {
    const catchSpy = vi.fn();
    const modalResult = { then: vi.fn().mockReturnValue({ catch: catchSpy }) };
    vi.spyOn(CloudrunLoadBalancerChoiceModal, 'show').mockReturnValue(modalResult as any);
    const stageConfig = new CloudrunEditLoadBalancerStageConfig({
      application: {},
      pipeline: {},
      stage: { loadBalancers: [] },
      updateStage: vi.fn(),
    } as any);

    (stageConfig as any).addLoadBalancer();

    expect(catchSpy).toHaveBeenCalled();
  });

  it('handles dismissal of the load balancer edit modal when editing a stage load balancer', () => {
    const catchSpy = vi.fn();
    const modalResult = { then: vi.fn().mockReturnValue({ catch: catchSpy }) };
    vi.spyOn(CloudrunLoadBalancerModal, 'show').mockReturnValue(modalResult as any);
    const stageConfig = new CloudrunEditLoadBalancerStageConfig({
      application: {},
      pipeline: {},
      stage: { loadBalancers: [{ name: 'default' }] },
      updateStage: vi.fn(),
    } as any);

    (stageConfig as any).editLoadBalancer(0);

    expect(catchSpy).toHaveBeenCalled();
  });

  it('updates details when application load balancer data refreshes', async () => {
    const firstLoadBalancer = { name: 'default', account: 'test', region: 'us-central1', image: 'old' };
    const refreshedLoadBalancer = { name: 'default', account: 'test', region: 'us-central1', image: 'new' };
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [firstLoadBalancer],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
      loadBalancers: {
        data: [firstLoadBalancer],
        ready: vi.fn().mockReturnValue(Promise.resolve()),
      },
    } as any;
    const onChange = vi.fn();
    const loadBalancerParams = { name: 'default', accountId: 'test', region: 'us-central1' } as any;

    function TestComponent() {
      const details = useCloudrunLoadBalancerDetails({
        app,
        loadBalancerParams,
        autoClose: vi.fn(),
      } as any);
      React.useEffect(() => onChange(details), [details.data, details.loading, details.error]);
      return null;
    }

    const wrapper = mount(React.createElement(TestComponent));
    await Promise.resolve();
    wrapper.setProps({});

    app.loadBalancers.data = [refreshedLoadBalancer];
    status$.next({ status: 'FETCHED', loaded: true, lastRefresh: 2, data: [refreshedLoadBalancer] });
    wrapper.setProps({});

    expect(onChange.mock.lastCall[0].data).toBe(refreshedLoadBalancer);
  });
});
