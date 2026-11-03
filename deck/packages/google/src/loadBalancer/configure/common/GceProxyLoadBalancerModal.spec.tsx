import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { UIRouterContext, UIRouterReact } from '@uirouter/react';

import { LoadBalancerWriter, TaskReader } from '@spinnaker/core';

import {
  GceProxyLoadBalancerModal,
  normalizeGceProxyLoadBalancerCommand,
  serializeGceProxyLoadBalancerCommand,
  submitGceProxyLoadBalancerCommand,
} from './GceProxyLoadBalancerModal';

describe('GceProxyLoadBalancerModal', () => {
  const application = {
    getDataSource: vi.fn(),
    loadBalancers: { onNextRefresh: vi.fn(), refresh: vi.fn() },
    name: 'app',
  } as any;

  it('normalizes the historical singular backend service contract for edit without losing references', () => {
    const command = normalizeGceProxyLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          affinityCookieTtlSec: 120,
          connectionDrainingTimeoutSec: 30,
          healthCheck: {
            checkIntervalSec: 10,
            healthCheckType: 'TCP',
            name: 'removed-check',
            port: 443,
            selfLink: 'projects/test/global/healthChecks/removed-check',
            timeoutSec: 5,
          },
          name: 'removed-backend',
          portName: 'tls',
          sessionAffinity: 'GENERATED_COOKIE',
        },
        certificate: 'projects/test/global/sslCertificates/removed-cert',
        ipAddress: 'projects/test/global/addresses/removed-address',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        loadBalancerType: 'SSL',
        portRange: '443',
        region: 'global',
      },
      'edit',
      'SSL',
    );

    expect(command.mode).toBe('edit');
    expect(command.listeners[0]).toEqual(
      expect.objectContaining({
        address: { name: 'removed-address', selfLink: 'projects/test/global/addresses/removed-address' },
        certificate: { name: 'removed-cert', selfLink: 'projects/test/global/sslCertificates/removed-cert' },
        protocol: 'SSL',
      }),
    );
    expect(command.backendServices[0]).toEqual(
      expect.objectContaining({
        affinityCookieTtlSec: 120,
        connectionDrainingTimeoutSec: 30,
        healthCheck: {
          name: 'removed-check',
          selfLink: 'projects/test/global/healthChecks/removed-check',
        },
        name: 'removed-backend',
        portName: 'tls',
        protocol: 'TCP',
        sessionAffinity: 'GENERATED_COOKIE',
      }),
    );
    expect(command.healthChecks[0]).toEqual(
      expect.objectContaining({ healthCheckType: 'TCP', name: 'removed-check', port: 443, timeoutSec: 5 }),
    );
  });

  it('serializes SSL frontend state to the compatible historical Clouddriver operation payload', () => {
    const command = normalizeGceProxyLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          connectionDrainingTimeoutSec: 30,
          name: 'app-main',
          portName: 'tls',
          sessionAffinity: 'NONE',
        },
        certificate: 'cert-a',
        healthChecks: [
          {
            checkIntervalSec: '15',
            healthCheckType: 'HTTP',
            healthyThreshold: '2',
            host: 'api.internal',
            name: 'check-a',
            port: '8080',
            proxyHeader: 'PROXY_V1',
            requestPath: 'ready',
            timeoutSec: '5',
            unhealthyThreshold: '3',
            useServingPort: false,
          },
        ],
        ipAddress: 'address-a',
        loadBalancerName: 'app-main',
        loadBalancerType: 'SSL',
        portRange: '443',
      },
      'pipeline',
      'SSL',
    );

    const payload = serializeGceProxyLoadBalancerCommand(command);

    expect(command.listeners[0].protocol).toBe('SSL');
    expect(payload).toEqual(
      expect.objectContaining({
        backendService: expect.objectContaining({
          connectionDrainingTimeoutSec: 30,
          healthCheck: expect.objectContaining({
            checkIntervalSec: 15,
            healthCheckType: 'HTTP',
            healthyThreshold: 2,
            host: 'api.internal',
            name: 'check-a',
            port: 8080,
            proxyHeader: 'PROXY_V1',
            requestPath: '/ready',
            timeoutSec: 5,
            unhealthyThreshold: 3,
            useServingPort: false,
          }),
          name: 'app-main',
          portName: 'tls',
          protocol: undefined,
          sessionAffinity: 'NONE',
        }),
        certificate: 'cert-a',
        cloudProvider: 'gce',
        credentials: 'account-a',
        ipAddress: 'address-a',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        loadBalancerType: 'SSL',
        name: 'app-main',
        portRange: '443',
        provider: 'gce',
        region: 'global',
        type: 'upsertLoadBalancer',
      }),
    );
    expect(payload.backendServices).toBeUndefined();
    expect(payload.healthChecks).toBeUndefined();
  });

  it('serializes INTERNAL listeners as regional ports with the selected backend protocol', () => {
    const command = normalizeGceProxyLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: { name: 'app-main', sessionAffinity: 'NONE' },
        healthChecks: [{ healthCheckType: 'TCP', name: 'check-a', port: 80 }],
        ipAddress: '10.0.0.2',
        ipProtocol: 'UDP',
        loadBalancerName: 'app-main',
        loadBalancerType: 'INTERNAL',
        network: 'network-a',
        ports: ['80', '443'],
        region: 'europe-west1',
        subnet: 'subnet-a',
      },
      'create',
      'INTERNAL',
    );

    const payload = serializeGceProxyLoadBalancerCommand(command);

    expect(payload).toEqual(
      expect.objectContaining({
        ipProtocol: 'UDP',
        network: 'network-a',
        ports: ['80', '443'],
        region: 'europe-west1',
        subnet: 'subnet-a',
      }),
    );
    expect(payload.portRange).toBeUndefined();
  });

  it('returns the operation payload through the modal in pipeline mode without starting a task', () => {
    const closeModal = vi.fn();
    const taskMonitor = { submit: vi.fn() } as any;
    const command = validCommand('pipeline');

    submitGceProxyLoadBalancerCommand(command, { application, closeModal, taskMonitor });

    expect(closeModal).toHaveBeenCalledExactlyOnceWith(serializeGceProxyLoadBalancerCommand(command));
    expect(taskMonitor.submit).not.toHaveBeenCalled();
  });

  (['create', 'edit'] as const).forEach((mode) => {
    it(`submits the Clouddriver payload through LoadBalancerWriter in infrastructure ${mode} mode`, () => {
      const upsert = vi.spyOn(LoadBalancerWriter, 'upsertLoadBalancer').mockReturnValue(new Promise(() => {}));
      let operation: (() => PromiseLike<any>) | undefined;
      const taskMonitor = {
        submit: vi.fn().mockImplementation((submitOperation: () => PromiseLike<any>) => {
          operation = submitOperation;
        }),
      } as any;
      const command = validCommand(mode);

      submitGceProxyLoadBalancerCommand(command, {
        application,
        closeModal: vi.fn(),
        taskMonitor,
      });
      operation?.();

      expect(upsert).toHaveBeenCalledWith(
        serializeGceProxyLoadBalancerCommand(command),
        application,
        mode === 'create' ? 'Create' : 'Update',
        { healthCheck: {} },
      );
    });
  });

  it('exposes pipeline support and locks infrastructure identity fields in edit mode', () => {
    expect(GceProxyLoadBalancerModal.supportsPipelineConfig).toBe(true);
    expect(typeof GceProxyLoadBalancerModal.show).toBe('function');
    const { container } = render(
      <GceProxyLoadBalancerModal
        app={application}
        closeModal={vi.fn()}
        dismissModal={vi.fn()}
        isNew={false}
        loadBalancer={
          {
            account: 'account-a',
            backendService: { name: 'app-main' },
            loadBalancerType: 'TCP',
            name: 'app-main',
            portRange: '443',
          } as any
        }
        loadBalancerType="TCP"
        readers={resolvedReaders()}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Edit app-main' })).toBeInTheDocument();
    ['name', 'credentials', 'region'].forEach((field) => expect(fieldControl(container, field)).toBeDisabled());
  });

  it('owns its refresh subscription through public submission and unmount', async () => {
    const unsubscribe = vi.fn();
    const callbacks: Array<() => void> = [];
    const onNextRefresh = vi.fn().mockImplementation((callback: () => void) => {
      callbacks.push(callback);
      return unsubscribe;
    });
    const refresh = vi.fn();
    const closeModal = vi.fn();
    const task = { id: 'task' } as any;
    vi.spyOn(LoadBalancerWriter, 'upsertLoadBalancer').mockResolvedValue(task);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    const router = new UIRouterReact();
    const rendered = render(
      <UIRouterContext.Provider value={router}>
        <GceProxyLoadBalancerModal
          application={{ getDataSource: vi.fn(), loadBalancers: { onNextRefresh, refresh }, name: 'app' } as any}
          closeModal={closeModal}
          isNew={false}
          loadBalancer={proxyLoadBalancer()}
          loadBalancerType="TCP"
          readers={resolvedReaders()}
        />
      </UIRouterContext.Provider>,
    );
    await waitFor(() => expect(screen.getByRole('button', { name: 'Update' })).toBeEnabled());

    fireEvent.click(screen.getByRole('button', { name: 'Update' }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());

    expect(onNextRefresh.mock.invocationCallOrder[0]).toBeLessThan(refresh.mock.invocationCallOrder[0]);

    rendered.unmount();
    callbacks[0]();

    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(closeModal).not.toHaveBeenCalled();
  });
});

function validCommand(mode: 'create' | 'edit' | 'pipeline'): any {
  return normalizeGceProxyLoadBalancerCommand(
    {
      account: 'account-a',
      backendService: { name: 'app-main', portName: 'tcp', sessionAffinity: 'NONE' },
      healthChecks: [{ healthCheckType: 'TCP', name: 'check-a', port: 443 }],
      loadBalancerName: 'app-main',
      loadBalancerType: 'TCP',
      portRange: '443',
    },
    mode,
    'TCP',
  );
}

function proxyLoadBalancer(): any {
  return {
    account: 'account-a',
    backendService: { name: 'app-main', portName: 'tcp', sessionAffinity: 'NONE' },
    healthChecks: [{ healthCheckType: 'TCP', name: 'check-a', port: 443 }],
    loadBalancerType: 'TCP',
    name: 'app-main',
    portRange: '443',
    region: 'global',
  };
}

function fieldControl(container: HTMLElement, name: string): HTMLInputElement | HTMLSelectElement {
  const control = container.querySelector(`[data-field="${name}"] input, [data-field="${name}"] select`);
  if (!(control instanceof HTMLInputElement || control instanceof HTMLSelectElement)) {
    throw new Error(`No control found for ${name}`);
  }
  return control;
}

function resolvedReaders(): any {
  return {
    accounts: () => Promise.resolve([]),
    addresses: () => Promise.resolve([]),
    backendServices: () => Promise.resolve([]),
    certificates: () => Promise.resolve([]),
    healthChecks: () => Promise.resolve([]),
    networks: () => Promise.resolve([]),
    regions: () => Promise.resolve([]),
    subnets: () => Promise.resolve([]),
  };
}
