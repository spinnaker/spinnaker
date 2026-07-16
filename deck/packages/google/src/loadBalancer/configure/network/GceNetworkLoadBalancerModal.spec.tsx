import React from 'react';
import { shallow } from 'enzyme';

import { GceNetworkLoadBalancerEditor } from './GceNetworkLoadBalancerEditor';
import {
  GceNetworkLoadBalancerModal,
  normalizeGceNetworkLoadBalancerCommand,
  serializeGceNetworkLoadBalancerCommand,
  submitGceNetworkLoadBalancerCommand,
} from './GceNetworkLoadBalancerModal';

describe('GceNetworkLoadBalancerModal', () => {
  const application = { name: 'app' } as any;

  it('normalizes persisted NETWORK details and resource references for edit', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        healthCheck: {
          checkIntervalSec: 15,
          healthyThreshold: 3,
          name: 'app-main-hc',
          port: '8080',
          requestPath: 'health',
          selfLink: 'projects/test/global/httpHealthChecks/app-main-hc',
          timeoutSec: 7,
          unhealthyThreshold: 4,
        },
        ipAddress: 'projects/test/regions/europe-west1/addresses/app-main-ip',
        ipProtocol: 'udp',
        loadBalancerName: 'app-main',
        network: 'projects/test/global/networks/default',
        portRange: 8080,
        region: 'europe-west1',
        sessionAffinity: 'client_ip_proto',
        targetPool: 'projects/test/regions/europe-west1/targetPools/app-main-tp',
      },
      'edit',
    );

    expect(command).toEqual(
      jasmine.objectContaining({
        credentials: 'account-a',
        loadBalancerType: 'NETWORK',
        mode: 'edit',
        name: 'app-main',
        network: { name: 'default', selfLink: 'projects/test/global/networks/default' },
        region: 'europe-west1',
        sessionAffinity: 'CLIENT_IP_PROTO',
        targetPool: {
          name: 'app-main-tp',
          selfLink: 'projects/test/regions/europe-west1/targetPools/app-main-tp',
        },
      }),
    );
    expect(command.listeners).toEqual([
      {
        address: {
          name: 'app-main-ip',
          selfLink: 'projects/test/regions/europe-west1/addresses/app-main-ip',
        },
        name: 'app-main',
        portRange: '8080',
        protocol: 'UDP',
      },
    ]);
    expect(command.healthChecks).toEqual([
      jasmine.objectContaining({
        checkIntervalSec: 15,
        healthyThreshold: 3,
        name: 'app-main-hc',
        port: 8080,
        requestPath: '/health',
        selfLink: 'projects/test/global/httpHealthChecks/app-main-hc',
        timeoutSec: 7,
        unhealthyThreshold: 4,
      }),
    ]);
  });

  it('normalizes the nested load-balancer details shape used by infrastructure edit', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        elb: {
          healthCheck: { port: 53, requestPath: '/health' },
          ipAddress: 'address-a',
          listenerDescriptions: [{ listener: { loadBalancerPort: '53', protocol: 'UDP' } }],
          sessionAffinity: 'CLIENT_IP',
        },
        name: 'app-main',
        network: 'network-a',
        region: 'europe-west1',
        targetPool: 'target-pool-a',
      },
      'edit',
    );

    expect(command.listeners[0]).toEqual({
      address: { name: 'address-a' },
      name: 'app-main',
      portRange: '53',
      protocol: 'UDP',
    });
    expect(command.healthChecks[0]).toEqual(jasmine.objectContaining({ port: 53, requestPath: '/health' }));
    expect(command.sessionAffinity).toBe('CLIENT_IP');
  });

  it('creates the historical NETWORK defaults without inventing persisted references', () => {
    const command = normalizeGceNetworkLoadBalancerCommand({}, 'create', 'app', {
      credentials: 'account-a',
      region: 'europe-west1',
    });

    expect(command).toEqual(
      jasmine.objectContaining({
        credentials: 'account-a',
        loadBalancerType: 'NETWORK',
        mode: 'create',
        name: 'app',
        network: undefined,
        region: 'europe-west1',
        sessionAffinity: 'NONE',
        targetPool: undefined,
      }),
    );
    expect(command.listeners).toEqual([{ name: 'app', portRange: '8080', protocol: 'TCP' }]);
    expect(command.healthChecks).toEqual([
      {
        checkIntervalSec: 10,
        healthCheckType: 'HTTP',
        healthyThreshold: 10,
        port: 80,
        requestPath: '/',
        timeoutSec: 5,
        unhealthyThreshold: 2,
      },
    ]);
  });

  ([null, undefined] as const).forEach((loadBalancer) => {
    it(`initializes infrastructure create when the persisted load balancer is ${String(loadBalancer)}`, () => {
      const wrapper = shallow(
        <GceNetworkLoadBalancerModal
          app={application}
          closeModal={jasmine.createSpy('closeModal')}
          data={emptyData()}
          dismissModal={jasmine.createSpy('dismissModal')}
          loadBalancer={loadBalancer as any}
          mode="create"
        />,
      );

      const command = wrapper.find(GceNetworkLoadBalancerEditor).prop('command');
      expect(command.mode).toBe('create');
      expect(command.listeners).toEqual([{ name: 'app', portRange: '8080', protocol: 'TCP' }]);
      expect(command.healthChecks).toEqual([
        {
          checkIntervalSec: 10,
          healthCheckType: 'HTTP',
          healthyThreshold: 10,
          port: 80,
          requestPath: '/',
          timeoutSec: 5,
          unhealthyThreshold: 2,
        },
      ]);
    });
  });

  it('serializes the exact Clouddriver NETWORK contract without editor-only network or persisted references', () => {
    const command = normalizeGceNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        healthCheck: {
          checkIntervalSec: 15,
          healthCheckType: 'HTTP',
          healthyThreshold: 3,
          name: 'persisted-name',
          port: 8080,
          requestPath: '/health',
          selfLink: 'projects/test/global/httpHealthChecks/persisted-name',
          timeoutSec: 7,
          unhealthyThreshold: 4,
        },
        ipAddress: 'address-a',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        network: 'network-a',
        portRange: '80-81',
        region: 'europe-west1',
        sessionAffinity: 'CLIENT_IP',
        targetPool: 'target-pool-a',
      },
      'pipeline',
    );

    const payload = serializeGceNetworkLoadBalancerCommand(command);

    expect(payload).toEqual({
      cloudProvider: 'gce',
      credentials: 'account-a',
      healthCheck: {
        checkIntervalSec: 15,
        healthyThreshold: 3,
        port: 8080,
        requestPath: '/health',
        timeoutSec: 7,
        unhealthyThreshold: 4,
      },
      ipAddress: 'address-a',
      ipProtocol: 'TCP',
      loadBalancerName: 'app-main',
      loadBalancerType: 'NETWORK',
      name: 'app-main',
      portRange: '80-81',
      provider: 'gce',
      region: 'europe-west1',
      sessionAffinity: 'CLIENT_IP',
      type: 'upsertLoadBalancer',
    });
    expect((payload as any).network).toBeUndefined();
    expect((payload as any).targetPool).toBeUndefined();
    expect((payload as any).healthChecks).toBeUndefined();
    expect((payload as any).listeners).toBeUndefined();
    expect((payload as any).mode).toBeUndefined();
  });

  it('returns only the normalized command in pipeline mode', () => {
    const executeTask = jasmine.createSpy('executeTask');
    const command = validCommand('pipeline');

    const result = submitGceNetworkLoadBalancerCommand(command, { application, executeTask });

    expect(result).toEqual(serializeGceNetworkLoadBalancerCommand(command));
    expect(executeTask).not.toHaveBeenCalled();
  });

  (['create', 'edit'] as const).forEach((mode) => {
    it(`executes the direct normalized job in infrastructure ${mode} mode`, () => {
      const task = Promise.resolve({ id: 'task' });
      const executeTask = jasmine.createSpy('executeTask').and.returnValue(task);
      const command = validCommand(mode);

      const result = submitGceNetworkLoadBalancerCommand(command, { application, executeTask });

      expect(result).toBe(task);
      expect(executeTask).toHaveBeenCalledOnceWith({
        application,
        description: `${mode === 'edit' ? 'Update' : 'Create'} Load Balancer: app-main`,
        job: [serializeGceNetworkLoadBalancerCommand(command)],
      });
    });
  });

  it('exposes pipeline support and passes edit mode to the editor', () => {
    expect(GceNetworkLoadBalancerModal.supportsPipelineConfig).toBe(true);
    expect(typeof GceNetworkLoadBalancerModal.show).toBe('function');
    const wrapper = shallow(
      <GceNetworkLoadBalancerModal
        app={application}
        closeModal={jasmine.createSpy('closeModal')}
        data={emptyData()}
        dismissModal={jasmine.createSpy('dismissModal')}
        isNew={false}
        loadBalancer={{ account: 'account-a', name: 'app-main', region: 'europe-west1' } as any}
      />,
    );

    expect(wrapper.find(GceNetworkLoadBalancerEditor).prop('command').mode).toBe('edit');
  });
});

function validCommand(mode: 'create' | 'edit' | 'pipeline'): any {
  return normalizeGceNetworkLoadBalancerCommand(
    {
      account: 'account-a',
      healthCheck: { port: 80, requestPath: '/' },
      ipAddress: 'address-a',
      ipProtocol: 'TCP',
      loadBalancerName: 'app-main',
      network: 'network-a',
      portRange: '80',
      region: 'europe-west1',
      sessionAffinity: 'NONE',
      targetPool: 'target-pool-a',
    },
    mode,
  );
}

function emptyData(): any {
  return {
    accounts: [],
    addresses: [],
    backendServices: [],
    certificates: [],
    healthChecks: [],
    networks: [],
    regions: [],
    subnets: [],
  };
}
