import React from 'react';
import { fireEvent, screen } from '@testing-library/react';

import { TaskExecutor } from '@spinnaker/core';
import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import { buildGceLoadBalancerJobs } from '../common';
import { validateGceHttpLoadBalancerCommand } from './GceHttpLoadBalancerEditor';
import { GceHttpLoadBalancerModal, initializeGceHttpLoadBalancerCommand } from './GceHttpLoadBalancerModal';

describe('GceHttpLoadBalancerModal', () => {
  const application = { name: 'test-app' } as any;
  const emptyData = {
    accounts: [],
    addresses: [],
    backendServices: [],
    certificates: [],
    healthChecks: [],
    networks: [],
    regions: [],
    subnets: [],
  };

  it('advertises pipeline support after returning exact pipeline commands', () => {
    expect((GceHttpLoadBalancerModal as any).supportsPipelineConfig).toBe(true);
  });

  it('initializes persisted composite data as an exact normalized nested command', () => {
    const command = initializeGceHttpLoadBalancerCommand(
      {
        account: 'account-a',
        backendServices: [{ healthCheck: 'removed-check', name: 'removed-backend', unknownField: 'keep' }],
        defaultService: 'removed-backend',
        healthChecks: [{ healthCheckType: 'http', name: 'removed-check', port: '80', requestPath: 'health' }],
        hostRules: [
          {
            hostPatterns: ['api.example.com'],
            pathMatcher: {
              defaultService: 'removed-backend',
              pathRules: [{ backendService: 'removed-backend', paths: ['/v1'] }],
            },
          },
        ],
        listeners: [
          {
            certificate: 'https://compute/sslCertificates/removed-cert',
            ipAddress: 'https://compute/addresses/removed-address',
            name: 'frontend',
            port: 443,
            protocol: 'https',
          },
        ],
        loadBalancerType: 'http',
        urlMapName: 'test-app-main',
      },
      'edit',
      application,
    );

    expect(command.name).toBe('test-app-main');
    expect(command.listeners[0]).toEqual({
      address: { name: 'removed-address', selfLink: 'https://compute/addresses/removed-address' },
      certificate: { name: 'removed-cert', selfLink: 'https://compute/sslCertificates/removed-cert' },
      name: 'frontend',
      portRange: '443',
      protocol: 'HTTPS',
    });
    expect(command.backendServices[0]).toEqual({
      healthCheck: { name: 'removed-check' },
      name: 'removed-backend',
      unknownField: 'keep',
    });
    expect(command.hostRules[0].pathMatcher.pathRules[0]).toEqual({
      backendService: { name: 'removed-backend' },
      paths: ['/v1'],
    });
  });

  it('returns exact operations without executing a task in pipeline-edit mode', () => {
    const closeModal = vi.fn();
    const executeTask = vi.spyOn(TaskExecutor, 'executeTask').mockReturnValue(undefined);
    const loadBalancer = {
      account: 'account-a',
      backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
      defaultService: 'backend-a',
      healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
      hostRules: [
        {
          hostPatterns: ['api.example.com'],
          pathMatcher: {
            defaultService: 'backend-a',
            pathRules: [{ backendService: 'backend-a', paths: ['/v1'] }],
          },
        },
      ],
      listeners: [{ ipAddress: 'address-a', name: 'frontend', port: 80, protocol: 'HTTP' }],
      loadBalancerType: 'HTTP',
      name: 'test-app-main',
    };
    const expectedOperations = buildGceLoadBalancerJobs(
      initializeGceHttpLoadBalancerCommand(loadBalancer, 'pipeline', application),
    );
    renderWithRouter(
      <GceHttpLoadBalancerModal
        app={application}
        closeModal={closeModal}
        data={emptyData}
        dismissModal={vi.fn()}
        forPipelineConfig={true}
        isNew={false}
        loadBalancer={loadBalancer}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    expect(executeTask).not.toHaveBeenCalled();
    expect(closeModal).toHaveBeenCalledExactlyOnceWith(expectedOperations);
    expect(expectedOperations.map(({ loadBalancerName }) => loadBalancerName)).toEqual(['frontend']);
  });

  it('executes an update task instead of returning operations in infrastructure-edit mode', () => {
    const closeModal = vi.fn();
    const executeTask = vi.fn().mockReturnValue(Promise.resolve({ id: 'task' }));
    const loadBalancer = {
      account: 'account-a',
      backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
      defaultService: 'backend-a',
      healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
      listeners: [{ ipAddress: 'address-a', name: 'frontend', port: 80, protocol: 'HTTP' }],
      loadBalancerType: 'HTTP',
      name: 'test-app-main',
    };
    const expectedOperations = buildGceLoadBalancerJobs(
      initializeGceHttpLoadBalancerCommand(loadBalancer, 'edit', application),
    );
    renderWithRouter(
      <GceHttpLoadBalancerModal
        app={application}
        closeModal={closeModal}
        data={emptyData}
        dismissModal={vi.fn()}
        executeTask={executeTask}
        isNew={false}
        loadBalancer={loadBalancer}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Update' }));

    expect(closeModal).not.toHaveBeenCalled();
    expect(executeTask).toHaveBeenCalledExactlyOnceWith({
      application,
      description: 'Update Load Balancer: test-app-main',
      job: expectedOperations,
    });
  });

  it('executes normalized listener jobs in infrastructure mode', () => {
    const executeTask = vi.fn().mockReturnValue(Promise.resolve({ id: 'task' }));
    renderWithRouter(
      <GceHttpLoadBalancerModal
        app={application}
        closeModal={vi.fn()}
        data={emptyData}
        dismissModal={vi.fn()}
        executeTask={executeTask}
        loadBalancer={{
          account: 'account-a',
          backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
          defaultService: 'backend-a',
          healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
          listeners: [
            { ipAddress: 'address-a', name: 'frontend-a', port: 80, protocol: 'HTTP' },
            { ipAddress: 'address-b', name: 'frontend-b', port: 8080, protocol: 'HTTP' },
          ],
          loadBalancerType: 'INTERNAL_MANAGED',
          name: 'test-app-main',
          network: 'network-a',
          region: 'europe-west1',
          subnet: 'subnet-a',
        }}
        mode="create"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(executeTask).toHaveBeenCalled();
    const jobs = executeTask.mock.lastCall[0].job;
    expect(jobs.map(({ name }: any) => name)).toEqual(['frontend-a', 'frontend-b']);
    expect(jobs[0]).toEqual(
      expect.objectContaining({
        ipAddress: 'address-a',
        ipProtocol: 'TCP',
        loadBalancerName: 'frontend-a',
        network: 'network-a',
        region: 'europe-west1',
        subnet: 'subnet-a',
      }),
    );
  });

  it('serializes INTERNAL_MANAGED HTTPS listeners with certificates', () => {
    const closeModal = vi.fn();
    renderWithRouter(
      <GceHttpLoadBalancerModal
        app={application}
        closeModal={closeModal}
        data={emptyData}
        dismissModal={vi.fn()}
        forPipelineConfig={true}
        loadBalancer={{
          account: 'account-a',
          backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
          defaultService: 'backend-a',
          healthChecks: [{ healthCheckType: 'HTTPS', name: 'check-a', port: 443, requestPath: '/health' }],
          listeners: [
            {
              certificate: 'regional-cert',
              name: 'internal-https',
              port: 443,
              protocol: 'HTTPS',
              subnet: 'subnet-a',
            },
          ],
          loadBalancerType: 'INTERNAL_MANAGED',
          name: 'test-app-internal',
          network: 'network-a',
          region: 'europe-west1',
          subnet: 'subnet-a',
        }}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    expect(closeModal).toHaveBeenCalledWith([
      expect.objectContaining({
        certificate: 'regional-cert',
        loadBalancerName: 'internal-https',
        portRange: '443',
      }),
    ]);
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    ['pipeline', 'infrastructure'].forEach((submissionMode) => {
      it(`rejects ${loadBalancerType} ${submissionMode} commands with an unresolved path default and non-443 HTTPS port`, () => {
        const closeModal = vi.fn();
        const executeTask = vi.fn();
        const props = {
          app: application,
          closeModal,
          data: emptyData,
          dismissModal: vi.fn(),
          executeTask,
          forPipelineConfig: submissionMode === 'pipeline',
          loadBalancer: {
            account: 'account-a',
            backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
            defaultService: 'backend-a',
            healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
            hostRules: [{ hostPatterns: ['api.example.com'], pathMatcher: { pathRules: [] } }],
            listeners: [{ certificate: 'cert-a', name: 'frontend', port: 443, protocol: 'HTTPS' }],
            loadBalancerType,
            name: 'test-app-main',
            network: loadBalancerType === 'INTERNAL_MANAGED' ? 'network-a' : undefined,
            region: loadBalancerType === 'INTERNAL_MANAGED' ? 'europe-west1' : 'global',
            subnet: loadBalancerType === 'INTERNAL_MANAGED' ? 'subnet-a' : undefined,
          },
          mode: submissionMode === 'infrastructure' ? 'create' : undefined,
        } as any;
        const command = initializeGceHttpLoadBalancerCommand(
          props.loadBalancer,
          submissionMode === 'pipeline' ? 'pipeline' : 'create',
          application,
        );
        command.listeners = [{ ...command.listeners[0], portRange: '8443' }];
        expect(validateGceHttpLoadBalancerCommand(command)).toEqual(
          expect.arrayContaining([
            'Path matcher default backend service is required.',
            'HTTPS listeners must use port 443.',
          ]),
        );
        renderWithRouter(<GceHttpLoadBalancerModal {...props} />);

        expect(executeTask).not.toHaveBeenCalled();
        expect(closeModal).not.toHaveBeenCalled();
        expect(screen.getByText('Path matcher default backend service is required.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: submissionMode === 'pipeline' ? 'Done' : 'Create' })).toBeDisabled();
      });
    });
  });

  ['pipeline', 'infrastructure'].forEach((submissionMode) => {
    it(`does not submit invalid ${submissionMode} commands`, () => {
      const closeModal = vi.fn();
      const executeTask = vi.fn();
      const props = {
        app: application,
        closeModal,
        data: emptyData,
        dismissModal: vi.fn(),
        executeTask,
        forPipelineConfig: submissionMode === 'pipeline',
        loadBalancer: {
          account: '',
          listeners: [{ name: '', port: 'not-a-port', protocol: 'HTTPS' }],
          loadBalancerType: 'HTTP',
          name: ' ',
        },
        mode: submissionMode === 'infrastructure' ? 'create' : undefined,
      } as any;
      renderWithRouter(<GceHttpLoadBalancerModal {...props} />);

      expect(executeTask).not.toHaveBeenCalled();
      expect(closeModal).not.toHaveBeenCalled();
      expect(screen.getByText('Name is required.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: submissionMode === 'pipeline' ? 'Done' : 'Create' })).toBeDisabled();
    });
  });

  it('renders modal controls as non-submit buttons', () => {
    const { container } = renderWithRouter(
      <GceHttpLoadBalancerModal
        app={application}
        closeModal={vi.fn()}
        data={emptyData}
        dismissModal={vi.fn()}
        loadBalancer={{ account: 'account-a', loadBalancerType: 'HTTP', name: 'test-app-main' } as any}
        mode="edit"
      />,
    );

    expect(
      Array.from(container.querySelectorAll('button')).every((button) => button.getAttribute('type') === 'button'),
    ).toBe(true);
  });
});
