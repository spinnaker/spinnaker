import React from 'react';
import { shallow } from 'enzyme';

import { TaskExecutor } from '@spinnaker/core';

import { buildGceLoadBalancerJobs } from '../common';
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
    const closeModal = jasmine.createSpy('closeModal');
    const executeTask = spyOn(TaskExecutor, 'executeTask');
    const modal = new GceHttpLoadBalancerModal({
      app: application,
      closeModal,
      dismissModal: jasmine.createSpy('dismissModal'),
      forPipelineConfig: true,
      isNew: false,
      loadBalancer: {
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
      },
      mode: 'edit',
    } as any);
    const expectedOperations = buildGceLoadBalancerJobs((modal as any).state.command);

    (modal as any).submit();

    expect((modal as any).state.command.mode).toBe('pipeline');
    expect(executeTask).not.toHaveBeenCalled();
    expect(closeModal).toHaveBeenCalledOnceWith(expectedOperations);
    expect(expectedOperations.map(({ loadBalancerName }) => loadBalancerName)).toEqual(['frontend']);
  });

  it('executes an update task instead of returning operations in infrastructure-edit mode', () => {
    const closeModal = jasmine.createSpy('closeModal');
    const executeTask = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({ id: 'task' }) as any);
    const modal = new GceHttpLoadBalancerModal({
      app: application,
      closeModal,
      dismissModal: jasmine.createSpy('dismissModal'),
      forPipelineConfig: false,
      isNew: false,
      loadBalancer: {
        account: 'account-a',
        backendServices: [{ healthCheck: 'check-a', name: 'backend-a', portName: 'http' }],
        defaultService: 'backend-a',
        healthChecks: [{ healthCheckType: 'HTTP', name: 'check-a', port: 80, requestPath: '/health' }],
        listeners: [{ ipAddress: 'address-a', name: 'frontend', port: 80, protocol: 'HTTP' }],
        loadBalancerType: 'HTTP',
        name: 'test-app-main',
      },
      mode: 'edit',
    } as any);
    const expectedOperations = buildGceLoadBalancerJobs((modal as any).state.command);

    (modal as any).submit();

    expect((modal as any).state.command.mode).toBe('edit');
    expect(closeModal).not.toHaveBeenCalled();
    expect(executeTask).toHaveBeenCalledOnceWith({
      application,
      description: 'Update Load Balancer: test-app-main',
      job: expectedOperations,
    });
  });

  it('executes normalized listener jobs in infrastructure mode', () => {
    const executeTask = jasmine.createSpy('executeTask').and.returnValue(Promise.resolve({ id: 'task' }));
    const modal = new GceHttpLoadBalancerModal({
      app: application,
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
      executeTask,
      loadBalancer: {
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
      },
      mode: 'create',
    } as any);

    (modal as any).submit();

    expect(executeTask).toHaveBeenCalled();
    const jobs = executeTask.calls.mostRecent().args[0].job;
    expect(jobs.map(({ name }: any) => name)).toEqual(['frontend-a', 'frontend-b']);
    expect(jobs[0]).toEqual(
      jasmine.objectContaining({
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
    const closeModal = jasmine.createSpy('closeModal');
    const modal = new GceHttpLoadBalancerModal({
      app: application,
      closeModal,
      dismissModal: jasmine.createSpy('dismissModal'),
      forPipelineConfig: true,
      loadBalancer: {
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
      },
    } as any);

    (modal as any).submit();

    expect(closeModal).toHaveBeenCalledWith([
      jasmine.objectContaining({
        certificate: 'regional-cert',
        loadBalancerName: 'internal-https',
        portRange: '443',
      }),
    ]);
  });

  (['HTTP', 'INTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    ['pipeline', 'infrastructure'].forEach((submissionMode) => {
      it(`rejects ${loadBalancerType} ${submissionMode} commands with an unresolved path default and non-443 HTTPS port`, () => {
        const closeModal = jasmine.createSpy('closeModal');
        const executeTask = jasmine.createSpy('executeTask');
        const props = {
          app: application,
          closeModal,
          data: emptyData,
          dismissModal: jasmine.createSpy('dismissModal'),
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
        const wrapper = shallow(<GceHttpLoadBalancerModal {...props} />);
        const modal = wrapper.instance() as any;
        modal.setState({
          command: {
            ...modal.state.command,
            listeners: [{ ...modal.state.command.listeners[0], portRange: '8443' }],
          },
        });
        wrapper.update();

        modal.submit();

        expect(executeTask).not.toHaveBeenCalled();
        expect(closeModal).not.toHaveBeenCalled();
        expect(wrapper.find('.gce-http-validation-errors').text()).toContain(
          'Path matcher default backend service is required.',
        );
        expect(wrapper.find('.gce-http-validation-errors').text()).toContain('HTTPS listeners must use port 443.');
        expect(wrapper.find('.btn-primary').prop('disabled')).toBe(true);
      });
    });
  });

  ['pipeline', 'infrastructure'].forEach((submissionMode) => {
    it(`does not submit invalid ${submissionMode} commands`, () => {
      const closeModal = jasmine.createSpy('closeModal');
      const executeTask = jasmine.createSpy('executeTask');
      const props = {
        app: application,
        closeModal,
        data: emptyData,
        dismissModal: jasmine.createSpy('dismissModal'),
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
      const modal = new GceHttpLoadBalancerModal(props);
      const wrapper = shallow(<GceHttpLoadBalancerModal {...props} />);

      (modal as any).submit();

      expect(executeTask).not.toHaveBeenCalled();
      expect(closeModal).not.toHaveBeenCalled();
      expect(wrapper.find('.gce-http-validation-errors').exists()).toBe(true);
      expect(wrapper.find('.btn-primary').prop('disabled')).toBe(true);
    });
  });

  it('renders modal controls as non-submit buttons', () => {
    const wrapper = shallow(
      <GceHttpLoadBalancerModal
        app={application}
        closeModal={jasmine.createSpy('closeModal')}
        data={emptyData}
        dismissModal={jasmine.createSpy('dismissModal')}
        loadBalancer={{ account: 'account-a', loadBalancerType: 'HTTP', name: 'test-app-main' } as any}
        mode="edit"
      />,
    );

    expect(wrapper.find('button').everyWhere((button) => button.prop('type') === 'button')).toBe(true);
  });
});
