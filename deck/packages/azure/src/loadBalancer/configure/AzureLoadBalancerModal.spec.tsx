import { LoadBalancerWriter, TaskMonitor } from '@spinnaker/core';

import { AzureLoadBalancerTypes } from '../../utility';
import { AzureLoadBalancerTransformer } from '../loadBalancer.transformer';
import {
  applyAzureLoadBalancerTypeDefaults,
  AzureLoadBalancerModalComponent as AzureLoadBalancerModal,
  findAzureVnetBySelectValue,
  formatInputValue,
  getAzureLoadBalancerTypeChoice,
  getAzureVnetSelectLabel,
  getAzureVnetSelectValue,
  listenerProtocolOptions,
  liftAzureLoadBalancerSessionPersistence,
  normalizeAzureLoadBalancerForSubmit,
  parseOptionalNumber,
  probeProtocolOptions,
  shouldRenderCreateFields,
  shouldRenderNetworkFields,
  shouldRenderSkuField,
  validateAzureLoadBalancerForSubmit,
  validSkus,
} from './AzureLoadBalancerModal';

describe('AzureLoadBalancerModal', () => {
  function buildModal(props: any, state: any = {}): any {
    const modal = Object.create(AzureLoadBalancerModal.prototype);
    modal.props = props;
    modal.state = { existingLoadBalancerNames: [], loadBalancer: null, ...state };
    modal.transformer = new AzureLoadBalancerTransformer(null);
    return modal;
  }

  describe('normalizeAzureLoadBalancerForSubmit', () => {
    it('does not copy selected vnet and subnet for Azure Load Balancer', () => {
      const loadBalancer = normalizeAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          clusterName: 'fnord-frontend',
          probes: [{ probeProtocol: 'TCP', probePath: '/health' }],
          loadBalancingRules: [{ protocol: 'TCP' }],
          selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a' },
          selectedSubnet: { name: 'subnet-a' },
          securityGroups: ['sg-1'],
        } as any,
        'Azure Load Balancer',
      );

      expect(loadBalancer.vnet).toBeUndefined();
      expect(loadBalancer.vnetResourceGroup).toBeUndefined();
      expect(loadBalancer.subnet).toBeUndefined();
      expect(loadBalancer.securityGroups).toBeNull();
    });

    it('preserves existing Azure Load Balancer vnet and subnet fields', () => {
      const loadBalancer = normalizeAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          probes: [{ probeProtocol: 'TCP' }],
          loadBalancingRules: [{ protocol: 'TCP' }],
          selectedVnet: { name: 'selected-vnet', resourceGroup: 'selected-rg' },
          selectedSubnet: { name: 'selected-subnet' },
          vnet: 'existing-vnet',
          vnetResourceGroup: 'existing-rg',
          subnet: 'existing-subnet',
        } as any,
        'Azure Load Balancer',
      );

      expect(loadBalancer.vnet).toBe('existing-vnet');
      expect(loadBalancer.vnetResourceGroup).toBe('existing-rg');
      expect(loadBalancer.subnet).toBe('existing-subnet');
    });

    it('copies selected vnet and subnet into the Application Gateway submit payload', () => {
      const loadBalancer = normalizeAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          clusterName: 'fnord-frontend',
          probes: [{ probeProtocol: 'HTTP', probePath: '/' }],
          loadBalancingRules: [{ protocol: 'HTTP' }],
          selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a' },
          selectedSubnet: { name: 'subnet-a' },
          securityGroups: ['sg-1'],
        } as any,
        'Azure Application Gateway',
      );

      expect(loadBalancer.vnet).toBe('vnet-a');
      expect(loadBalancer.vnetResourceGroup).toBe('rg-a');
      expect(loadBalancer.subnet).toBe('subnet-a');
      expect(loadBalancer.type).toBe('upsertLoadBalancer');
      expect(loadBalancer.loadBalancerType).toBe('Azure Application Gateway');
      expect(loadBalancer.probes[0].probeName).toBe('fnord-frontend-probe');
      expect(loadBalancer.loadBalancingRules[0].ruleName).toBe('fnord-frontend-rule0');
      expect(loadBalancer.loadBalancingRules[0].probeName).toBe('fnord-frontend-probe');
      expect(loadBalancer.securityGroups).toEqual(['sg-1']);
    });

    it('clears tcp probe paths and nulls security groups without vnet or subnet type', () => {
      const loadBalancer = normalizeAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          probes: [{ probeProtocol: 'TCP', probePath: '/health' }],
          loadBalancingRules: [{ protocol: 'TCP' }, { protocol: 'TCP' }],
          securityGroups: ['sg-1'],
        } as any,
        'Azure Load Balancer',
      );

      expect(loadBalancer.securityGroups).toBeNull();
      expect(loadBalancer.probes[0].probePath).toBeUndefined();
      expect(loadBalancer.probes[0].probeName).toBe('fnord-frontend-probe');
      expect(loadBalancer.loadBalancingRules[0].ruleName).toBe('fnord-frontend-rule0');
      expect(loadBalancer.loadBalancingRules[1].ruleName).toBe('fnord-frontend-rule1');
    });

    it('keeps empty numeric listener fields empty so validation can reject them', () => {
      const loadBalancer = normalizeAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          credentials: 'test',
          region: 'westus',
          probes: [{ probeProtocol: 'TCP', probePort: 80 }],
          loadBalancingRules: [{ protocol: 'TCP', externalPort: '', backendPort: '' }],
        } as any,
        'Azure Load Balancer',
      );

      expect(loadBalancer.loadBalancingRules[0].externalPort).toBe('');
      expect(loadBalancer.loadBalancingRules[0].backendPort).toBe('');
      expect(validateAzureLoadBalancerForSubmit(loadBalancer, 'Azure Load Balancer', { isNew: true })).toContain(
        'Listener 1 external port is required.',
      );
      expect(validateAzureLoadBalancerForSubmit(loadBalancer, 'Azure Load Balancer', { isNew: true })).toContain(
        'Listener 1 backend port is required.',
      );
    });

    it('strips stale rule-level persistence and keeps top-level session persistence', () => {
      const loadBalancer = normalizeAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          sessionPersistence: 'Client IP',
          probes: [{ probeProtocol: 'TCP', probePort: 80 }],
          loadBalancingRules: [
            { protocol: 'TCP', externalPort: 80, backendPort: 80, persistence: 'None', sessionPersistence: 'None' },
          ],
        } as any,
        'Azure Load Balancer',
      );

      expect(loadBalancer.sessionPersistence).toBe('Client IP');
      expect(loadBalancer.loadBalancingRules[0].persistence).toBeUndefined();
      expect(loadBalancer.loadBalancingRules[0].sessionPersistence).toBeUndefined();
    });
  });

  describe('load balancer type behavior', () => {
    it('infers the load balancer type from an existing command when the type prop is missing', () => {
      expect(getAzureLoadBalancerTypeChoice({ loadBalancerType: 'Azure Application Gateway' }).type).toBe(
        'Azure Application Gateway',
      );
      expect(getAzureLoadBalancerTypeChoice({ loadBalancerType: 'APPLICATION_GATEWAY' }).type).toBe(
        'Azure Application Gateway',
      );
    });

    it('uses Azure Load Balancer protocol options and defaults', () => {
      const command = applyAzureLoadBalancerTypeDefaults(
        { probes: [{}], loadBalancingRules: [{}] },
        'Azure Load Balancer',
      );

      expect(listenerProtocolOptions('Azure Load Balancer')).toEqual(['TCP', 'UDP']);
      expect(probeProtocolOptions('Azure Load Balancer')).toEqual(['TCP', 'HTTP']);
      expect(command.loadBalancingRules[0].protocol).toBe('TCP');
      expect(command.probes[0].probeProtocol).toBe('TCP');
      expect(command.sessionPersistence).toBe('None');
      expect(command.loadBalancingRules[0].sessionPersistence).toBeUndefined();
    });

    it('preserves top-level Azure Load Balancer session persistence in defaults and submit payload', () => {
      const command = applyAzureLoadBalancerTypeDefaults(
        {
          sessionPersistence: 'Client IP',
          probes: [{ probeProtocol: 'TCP' }],
          loadBalancingRules: [{ protocol: 'TCP' }],
        },
        'Azure Load Balancer',
      );
      const payload = normalizeAzureLoadBalancerForSubmit(command, 'Azure Load Balancer');

      expect(command.sessionPersistence).toBe('Client IP');
      expect(command.loadBalancingRules[0].sessionPersistence).toBeUndefined();
      expect(payload.sessionPersistence).toBe('Client IP');
    });

    it('lifts edit-mode Azure Load Balancer persistence to top-level session persistence', () => {
      const command = liftAzureLoadBalancerSessionPersistence(
        {
          elb: { loadBalancingRules: [{ persistence: 'Client IP and protocol' }] },
          loadBalancingRules: [{ protocol: 'TCP' }],
        },
        'Azure Load Balancer',
      );

      expect(command.sessionPersistence).toBe('Client IP and protocol');
    });

    it('prefers edit-mode top-level elb session persistence over rule persistence', () => {
      const command = liftAzureLoadBalancerSessionPersistence(
        {
          elb: { sessionPersistence: 'Client IP', loadBalancingRules: [{ persistence: 'Client IP and protocol' }] },
          loadBalancingRules: [{ protocol: 'TCP' }],
        },
        'Azure Load Balancer',
      );

      expect(command.sessionPersistence).toBe('Client IP');
    });

    it('initializes edit-mode top-level elb session persistence through the modal path', () => {
      const modal = buildModal({
        app: { name: 'fnord' },
        application: { name: 'fnord' },
        isNew: false,
        loadBalancerType: AzureLoadBalancerTypes[0],
        loadBalancer: {
          account: 'test',
          detail: 'frontend',
          elb: {
            loadBalancingRules: [{ persistence: 'Client IP and protocol', protocol: 'TCP' }],
            probes: [{ probePort: 80, probeProtocol: 'TCP' }],
            sessionPersistence: 'Client IP',
          },
          name: 'fnord-frontend',
          region: 'westus',
          stack: 'frontend',
        },
      });

      expect(modal.initializeLoadBalancer().sessionPersistence).toBe('Client IP');
    });

    it('initializes saved pipeline commands without converting from live load balancer shape', () => {
      const pipelineCommand = {
        credentials: 'test',
        detail: 'frontend',
        loadBalancerType: 'Azure Application Gateway',
        loadBalancingRules: [{ backendPort: 80, externalPort: 80, protocol: 'HTTP' }],
        name: 'fnord-frontend',
        probes: [{ probePath: '/', probePort: 80, probeProtocol: 'HTTP' }],
        region: 'westus',
        stack: 'frontend',
        subnet: 'subnet-a',
        type: 'upsertLoadBalancer',
        vnet: 'vnet-a',
        vnetResourceGroup: 'rg-a',
      };
      const modal = buildModal({
        app: { name: 'fnord' },
        application: { name: 'fnord' },
        forPipelineConfig: true,
        isNew: false,
        loadBalancer: pipelineCommand,
      });

      const command = modal.initializeLoadBalancer();

      expect(command.credentials).toBe('test');
      expect(command.region).toBe('westus');
      expect(command.probes[0].probePort).toBe(80);
      expect(command.loadBalancingRules[0].backendPort).toBe(80);
      expect(command.vnet).toBe('vnet-a');
      expect(command.vnetResourceGroup).toBe('rg-a');
      expect(command.subnet).toBe('subnet-a');
    });

    it('uses Application Gateway protocol options and defaults', () => {
      const command = applyAzureLoadBalancerTypeDefaults(
        { probes: [{}], loadBalancingRules: [{}] },
        'Azure Application Gateway',
      );

      expect(listenerProtocolOptions('Azure Application Gateway')).toEqual(['HTTP']);
      expect(probeProtocolOptions('Azure Application Gateway')).toEqual(['HTTP']);
      expect(command.loadBalancingRules[0].protocol).toBe('HTTP');
      expect(command.probes[0].probeProtocol).toBe('HTTP');
    });

    it('preserves advanced settings when applying protocol defaults', () => {
      const command = applyAzureLoadBalancerTypeDefaults(
        {
          probes: [{ probeProtocol: 'TCP', probeInterval: 15, unhealthyThreshold: 3, timeout: 60 }],
          loadBalancingRules: [{ protocol: 'TCP', idleTimeout: 12, sessionPersistence: 'Client IP' }],
        },
        'Azure Load Balancer',
      );

      expect(command.probes[0].probeInterval).toBe(15);
      expect(command.probes[0].unhealthyThreshold).toBe(3);
      expect(command.probes[0].timeout).toBe(60);
      expect(command.loadBalancingRules[0].idleTimeout).toBe(12);
      expect(command.sessionPersistence).toBe('None');
    });
  });

  describe('validation', () => {
    it('rejects missing stack and detail in create mode', () => {
      const errors = validateAzureLoadBalancerForSubmit(
        {
          name: 'fnord',
          credentials: 'test',
          region: 'westus',
          stack: '',
          detail: '',
          probes: [{ probeProtocol: 'TCP', probePort: 80 }],
          loadBalancingRules: [{ protocol: 'TCP', externalPort: 80, backendPort: 80 }],
        },
        'Azure Load Balancer',
        { isNew: true },
      );

      expect(errors).toContain('Stack is required.');
      expect(errors).toContain('Detail is required.');
    });

    it('matches legacy stack and detail patterns', () => {
      const errors = validateAzureLoadBalancerForSubmit(
        {
          name: 'fnord-front-end',
          credentials: 'test',
          region: 'westus',
          stack: 'front-end',
          detail: 'front_end',
          probes: [{ probeProtocol: 'TCP', probePort: 80 }],
          loadBalancingRules: [{ protocol: 'TCP', externalPort: 80, backendPort: 80 }],
        },
        'Azure Load Balancer',
        { isNew: true },
      );

      expect(errors).toContain('Stack may only contain letters and numbers.');
      expect(errors).toContain('Detail may only contain letters, numbers, and hyphens.');
    });

    it('rejects duplicate names and missing Application Gateway network fields', () => {
      const errors = validateAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          credentials: 'test',
          region: 'westus',
          stack: 'frontend',
          detail: '',
          probes: [{ probeProtocol: 'HTTP', probePort: 80, probePath: '/' }],
          loadBalancingRules: [{ protocol: 'HTTP', externalPort: 80, backendPort: 80 }],
        },
        'Azure Application Gateway',
        { isNew: true, existingLoadBalancerNames: ['fnord-frontend'] },
      );

      expect(errors).toContain('A load balancer named fnord-frontend already exists in this account and region.');
      expect(errors).toContain('VNet is required.');
      expect(errors).toContain('Subnet is required.');
    });

    it('rejects duplicate names case-insensitively', () => {
      const errors = validateAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          credentials: 'test',
          region: 'westus',
          stack: 'frontend',
          detail: 'blue',
          probes: [{ probeProtocol: 'TCP', probePort: 80 }],
          loadBalancingRules: [{ protocol: 'TCP', externalPort: 80, backendPort: 80 }],
        },
        'Azure Load Balancer',
        { isNew: true, existingLoadBalancerNames: ['FNORD-FRONTEND'] },
      );

      expect(errors).toContain('A load balancer named fnord-frontend already exists in this account and region.');
    });

    it('rejects overlong names and HTTP probes without paths', () => {
      const errors = validateAzureLoadBalancerForSubmit(
        {
          name: 'a'.repeat(33),
          credentials: 'test',
          region: 'westus',
          probes: [{ probeProtocol: 'HTTP', probePort: 80 }],
          loadBalancingRules: [{ protocol: 'TCP', externalPort: 80, backendPort: 80 }],
        },
        'Azure Load Balancer',
        { isNew: false },
      );

      expect(errors).toContain('Name must be 32 characters or fewer.');
      expect(errors).toContain('Health check path is required.');
    });

    it('rejects negative numeric values', () => {
      const errors = validateAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          credentials: 'test',
          region: 'westus',
          probes: [{ probeProtocol: 'TCP', probePort: -1, probeInterval: -1, unhealthyThreshold: -1 }],
          loadBalancingRules: [{ protocol: 'TCP', externalPort: -1, backendPort: -1, idleTimeout: -1 }],
        },
        'Azure Load Balancer',
        { isNew: false },
      );
      const applicationGatewayErrors = validateAzureLoadBalancerForSubmit(
        {
          name: 'fnord-frontend',
          credentials: 'test',
          region: 'westus',
          probes: [{ probeProtocol: 'HTTP', probePort: 80, probePath: '/', timeout: -1 }],
          loadBalancingRules: [{ protocol: 'HTTP', externalPort: 80, backendPort: 80 }],
        },
        'Azure Application Gateway',
        { isNew: false },
      );

      expect(errors).toContain('Listener 1 external port must be 0 or greater.');
      expect(errors).toContain('Listener 1 backend port must be 0 or greater.');
      expect(errors).toContain('Listener 1 idle timeout must be 0 or greater.');
      expect(errors).toContain('Health check port must be 0 or greater.');
      expect(errors).toContain('Health check interval must be 0 or greater.');
      expect(errors).toContain('Unhealthy threshold must be 0 or greater.');
      expect(applicationGatewayErrors).toContain('Health check timeout must be 0 or greater.');
    });

    it('parses empty optional numbers as undefined', () => {
      expect(parseOptionalNumber('')).toBeUndefined();
      expect(parseOptionalNumber('8080')).toBe(8080);
    });
  });

  describe('submit', () => {
    it('returns the normalized command without submitting a task for pipeline configuration', () => {
      const closeModal = jasmine.createSpy('closeModal');
      const upsertLoadBalancer = spyOn(LoadBalancerWriter, 'upsertLoadBalancer').and.callFake(
        () => Promise.reject({}) as any,
      );
      const application = {
        defaultCredentials: { azure: 'test' },
        defaultRegions: { azure: 'westus' },
        getDataSource: () => null,
        name: 'fnord',
      };
      spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
        dismiss: () => null,
        result: Promise.resolve(),
      } as any);
      const modal = new AzureLoadBalancerModal({
        app: application,
        application,
        closeModal,
        forPipelineConfig: true,
        isNew: true,
        loadBalancer: null,
        loadBalancerType: AzureLoadBalancerTypes[0],
      } as any);

      modal.state = {
        ...modal.state,
        accountsLoaded: true,
        loadBalancer: {
          credentials: 'test',
          detail: 'frontend',
          loadBalancingRules: [{ backendPort: 80, externalPort: 80, protocol: 'TCP' }],
          name: 'fnord-frontend',
          probes: [{ probePort: 80, probeProtocol: 'TCP' }],
          region: 'westus',
          stack: 'frontend',
        },
      };

      (modal as any).submit();

      expect(upsertLoadBalancer).not.toHaveBeenCalled();
      expect(closeModal).toHaveBeenCalledWith(
        jasmine.objectContaining({
          loadBalancerType: 'Azure Load Balancer',
          name: 'fnord-frontend',
          type: 'upsertLoadBalancer',
        }),
      );
    });

    it('submits the normalized command through TaskMonitor outside pipeline configuration', () => {
      const upsertLoadBalancer = spyOn(LoadBalancerWriter, 'upsertLoadBalancer').and.returnValue(
        Promise.resolve({} as any),
      );
      const application = {
        defaultCredentials: { azure: 'test' },
        defaultRegions: { azure: 'westus' },
        getDataSource: () => null,
        loadBalancers: {
          onNextRefresh: jasmine.createSpy('onNextRefresh'),
          refresh: jasmine.createSpy('refresh'),
        },
        name: 'fnord',
      };
      spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
        dismiss: () => null,
        result: Promise.resolve(),
      } as any);
      const modal = new AzureLoadBalancerModal({
        app: application,
        application,
        closeModal: jasmine.createSpy('closeModal'),
        forPipelineConfig: false,
        isNew: true,
        loadBalancer: null,
        loadBalancerType: AzureLoadBalancerTypes[0],
      } as any);
      spyOn(modal as any, 'setState').and.callFake((state: any) => {
        modal.state = { ...modal.state, ...state };
      });
      const submitTask = spyOn(modal.state.taskMonitor, 'submit').and.callFake((submitMethod: any) => submitMethod());

      modal.state = {
        ...modal.state,
        accountsLoaded: true,
        loadBalancer: {
          clusterName: 'fnord-frontend',
          credentials: 'test',
          detail: 'frontend',
          loadBalancingRules: [{ backendPort: 80, externalPort: 80, protocol: 'TCP' }],
          name: 'fnord-frontend',
          probes: [{ probePort: 80, probeProtocol: 'TCP' }],
          region: 'westus',
          stack: 'frontend',
        },
      };

      (modal as any).submit();

      expect(submitTask).toHaveBeenCalled();
      expect(upsertLoadBalancer).toHaveBeenCalledWith(
        jasmine.objectContaining({
          loadBalancerType: 'Azure Load Balancer',
          name: 'fnord-frontend',
          type: 'upsertLoadBalancer',
        }),
        application,
        'Create',
        {
          appName: 'fnord',
          cloudProvider: 'azure',
          clusterName: 'fnord-frontend',
          loadBalancerName: 'fnord-frontend',
          resourceGroupName: 'fnord-frontend',
        },
      );
    });
  });

  describe('rendering helpers', () => {
    it('preserves zero input values for rendering', () => {
      expect(formatInputValue(0)).toBe(0);
      expect(formatInputValue(null)).toBe('');
      expect(formatInputValue(undefined)).toBe('');
    });

    it('distinguishes vnets with the same name in different resource groups', () => {
      const vnets = [
        { name: 'shared', resourceGroup: 'rg-a' },
        { name: 'shared', resourceGroup: 'rg-b' },
      ] as any[];

      expect(findAzureVnetBySelectValue(vnets, getAzureVnetSelectValue(vnets[1]))).toBe(vnets[1]);
      expect(getAzureVnetSelectLabel(vnets[0])).toBe('shared (rg-a)');
      expect(getAzureVnetSelectLabel(vnets[1])).toBe('shared (rg-b)');
    });

    it('does not render create-only location fields in edit mode', () => {
      expect(shouldRenderCreateFields(false)).toBe(false);
      expect(shouldRenderCreateFields(true)).toBe(true);
    });

    it('only renders network fields for Application Gateway create', () => {
      expect(shouldRenderNetworkFields(false, 'Azure Application Gateway')).toBe(false);
      expect(shouldRenderNetworkFields(true, 'Azure Load Balancer')).toBe(false);
      expect(shouldRenderNetworkFields(true, 'Azure Application Gateway')).toBe(true);
    });

    it('only renders sku for Application Gateway create', () => {
      expect(shouldRenderSkuField(false, 'Azure Application Gateway')).toBe(false);
      expect(shouldRenderSkuField(true, 'Azure Load Balancer')).toBe(false);
      expect(shouldRenderSkuField(true, 'Azure Application Gateway')).toBe(true);
    });

    it('constrains Application Gateway sku values to legacy options', () => {
      expect(validSkus).toEqual(['Standard_v2', 'Standard_Small']);
    });
  });
});
