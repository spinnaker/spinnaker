import React from 'react';
import { shallow } from 'enzyme';

import { GceRegionalExternalNetworkLoadBalancerEditor } from './GceRegionalExternalNetworkLoadBalancerEditor';
import {
  GceRegionalExternalNetworkLoadBalancerModal,
  normalizeGceRegionalExternalNetworkLoadBalancerCommand,
  serializeGceRegionalExternalNetworkLoadBalancerCommand,
  submitGceRegionalExternalNetworkLoadBalancerCommand,
} from './GceRegionalExternalNetworkLoadBalancerModal';

describe('GceRegionalExternalNetworkLoadBalancerModal', () => {
  const application = { name: 'app' } as any;

  it('normalizes persisted REGIONAL_EXTERNAL_NETWORK details for edit', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: {
            healthCheckType: 'TCP',
            name: 'tcp-check',
            port: 80,
            selfLink: 'projects/test/regions/europe-west1/healthChecks/tcp-check',
          },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'tcp',
        loadBalancerName: 'app-main',
        networkTier: 'PREMIUM',
        ports: ['80', '443'],
        region: 'europe-west1',
      },
      'edit',
    );

    expect(command).toEqual(
      jasmine.objectContaining({
        credentials: 'account-a',
        loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
        mode: 'edit',
        name: 'app-main',
        networkTier: 'PREMIUM',
        ports: ['80', '443'],
        region: 'europe-west1',
      }),
    );
    expect(command.listeners).toEqual([
      {
        address: { name: '35.1.2.3' },
        name: 'app-main',
        portRange: '80,443',
        protocol: 'TCP',
      },
    ]);
    expect(command.backendServices[0]).toEqual(
      jasmine.objectContaining({
        healthCheck: jasmine.objectContaining({ name: 'tcp-check', port: 80 }),
        name: 'app-main',
        sessionAffinity: 'CLIENT_IP',
      }),
    );
  });

  it('serializes the exact Clouddriver REGIONAL_EXTERNAL_NETWORK contract', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        networkTier: 'PREMIUM',
        ports: ['80', '443'],
        region: 'europe-west1',
      },
      'pipeline',
    );

    const payload = serializeGceRegionalExternalNetworkLoadBalancerCommand(command);

    expect(payload).toEqual({
      backendService: {
        healthCheck: { healthCheckType: 'TCP', name: 'tcp-check', port: 80 },
        name: 'app-main',
        sessionAffinity: 'CLIENT_IP',
      },
      cloudProvider: 'gce',
      credentials: 'account-a',
      ipAddress: '35.1.2.3',
      ipProtocol: 'TCP',
      loadBalancerName: 'app-main',
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'app-main',
      networkTier: 'PREMIUM',
      ports: ['80', '443'],
      provider: 'gce',
      region: 'europe-west1',
      type: 'upsertLoadBalancer',
    });
    expect((payload as any).portRange).toBeUndefined();
    expect((payload as any).backendServices).toBeUndefined();
    expect((payload as any).healthChecks).toBeUndefined();
    expect((payload as any).listeners).toBeUndefined();
  });

  it('returns a named health check in pipeline mode when the persisted name is omitted', () => {
    const executeTask = jasmine.createSpy('executeTask');
    const persisted = {
      account: 'account-a',
      backendService: {
        healthCheck: { healthCheckType: 'TCP', port: 80 },
        name: 'app-main',
        sessionAffinity: 'NONE',
      },
      ipProtocol: 'TCP',
      loadBalancerName: 'app-main',
      ports: ['80'],
      region: 'europe-west1',
    };
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(persisted, 'pipeline');

    const result = submitGceRegionalExternalNetworkLoadBalancerCommand(command, { application, executeTask });

    expect(result).toEqual(serializeGceRegionalExternalNetworkLoadBalancerCommand(command));
    expect((result as any).backendService.healthCheck.name).toBe('app-main');
    expect(command.backendServices[0].healthCheck).toBe(command.healthChecks[0]);
    expect(persisted.backendService.healthCheck).toEqual({ healthCheckType: 'TCP', port: 80 });
    expect(executeTask).not.toHaveBeenCalled();
  });

  it('serializes the referenced pipeline health check through the backend service', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: 'tcp-check',
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        healthChecks: [{ healthCheckType: 'TCP', name: 'tcp-check', port: 80 }],
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        ports: ['80'],
        region: 'europe-west1',
      },
      'pipeline',
    );

    const payload = serializeGceRegionalExternalNetworkLoadBalancerCommand(command);

    expect(command.backendServices[0].healthCheck).toBe(command.healthChecks[0]);
    expect(payload.backendService.healthCheck).toEqual({
      healthCheckType: 'TCP',
      name: 'tcp-check',
      port: 80,
    });
  });

  it('serializes a named health check for direct edit when the persisted name is omitted', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', port: 80 },
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        ports: ['80'],
        region: 'europe-west1',
      },
      'edit',
    );

    const payload = serializeGceRegionalExternalNetworkLoadBalancerCommand(command);

    expect(payload.backendService.healthCheck.name).toBe('app-main');
  });

  it('preserves ipAddress and networkTier when direct edit omits optional address fields', () => {
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'UDP', name: 'udp-check', port: 53 },
          name: 'app-main',
          sessionAffinity: 'CLIENT_IP_PROTO',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'UDP',
        loadBalancerName: 'app-main',
        networkTier: 'PREMIUM',
        ports: ['53'],
        region: 'europe-west1',
      },
      'edit',
    );
    command.listeners[0].address = undefined;
    command.networkTier = undefined;

    const payload = serializeGceRegionalExternalNetworkLoadBalancerCommand(command);

    expect(payload.ipAddress).toBe('35.1.2.3');
    expect(payload.networkTier).toBe('PREMIUM');
    expect(payload.ipProtocol).toBe('UDP');
  });

  it('executes the direct normalized job in infrastructure create mode', () => {
    const task = Promise.resolve({ id: 'task' });
    const executeTask = jasmine.createSpy('executeTask').and.returnValue(task);
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      {
        account: 'account-a',
        backendService: {
          healthCheck: { healthCheckType: 'TCP', port: 80 },
          name: 'app-main',
          sessionAffinity: 'NONE',
        },
        ipAddress: '35.1.2.3',
        ipProtocol: 'TCP',
        loadBalancerName: 'app-main',
        networkTier: 'PREMIUM',
        ports: ['80'],
        region: 'europe-west1',
      },
      'create',
    );

    const result = submitGceRegionalExternalNetworkLoadBalancerCommand(command, { application, executeTask });

    expect(command.backendServices[0].healthCheck).toBe(command.healthChecks[0]);
    expect((command.backendServices[0].healthCheck as any).name).toBe('app-main');
    expect(result).toBe(task);
    expect(executeTask).toHaveBeenCalledOnceWith({
      application,
      description: 'Create Load Balancer: app-main',
      job: [serializeGceRegionalExternalNetworkLoadBalancerCommand(command)],
    });
  });

  it('exposes pipeline support and passes edit mode to the editor', () => {
    expect(GceRegionalExternalNetworkLoadBalancerModal.supportsPipelineConfig).toBe(true);
    const wrapper = shallow(
      <GceRegionalExternalNetworkLoadBalancerModal
        app={application}
        closeModal={jasmine.createSpy('closeModal')}
        data={emptyData()}
        dismissModal={jasmine.createSpy('dismissModal')}
        isNew={false}
        loadBalancer={{ account: 'account-a', name: 'app-main', region: 'europe-west1' } as any}
      />,
    );

    expect(wrapper.find(GceRegionalExternalNetworkLoadBalancerEditor).prop('command').mode).toBe('edit');
  });
});

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
