import {
  CloudProviderRegistry,
  DeployInitializer,
  DeploymentStrategySelector,
  MapEditor,
  NetworkReader,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';
import { shallow } from 'enzyme';
import React from 'react';

import { registerAzureProvider } from '../../../azure.module';
import { AzureServerGroupTransformer } from '../../serverGroup.transformer';
import { AzureServerGroupConfigurationService } from '../serverGroupConfiguration.service';
import {
  AzureCloneServerGroupModal as RoutedAzureCloneServerGroupModal,
  AzureCloneServerGroupModalComponent as AzureCloneServerGroupModal,
} from './AzureCloneServerGroupModal';
import {
  ServerGroupAdvancedSettings,
  ServerGroupBasicSettings,
  ServerGroupHealthSettings,
  ServerGroupImageSettings,
  ServerGroupLoadBalancers,
  ServerGroupNetworkSettings,
  ServerGroupTags,
} from './pages';

describe('AzureCloneServerGroupModal', () => {
  let runtimeServices: any;
  const application = {
    name: 'fnord',
    serverGroups: {
      refresh: jasmine.createSpy('refresh'),
      onNextRefresh: jasmine.createSpy('onNextRefresh'),
    },
  } as any;

  function command(overrides: any = {}): any {
    return {
      application: 'fnord',
      credentials: 'test',
      region: 'westus',
      selectedProvider: 'azure',
      stack: 'web',
      freeFormDetails: 'main',
      instanceType: 'Standard_DS1_v2',
      sku: { capacity: 2 },
      selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a' },
      subnet: 'subnet-a',
      loadBalancerName: 'lb-a',
      loadBalancerType: 'Azure Load Balancer',
      securityGroupName: 'sg-a',
      instanceTags: {},
      dataDisks: [],
      zonesEnabled: true,
      zones: ['1'],
      viewState: { mode: 'createPipeline', submitButtonLabel: 'Done' },
      backingData: {
        accounts: ['test'],
        filtered: { regions: [{ name: 'westus' }], loadBalancers: [], securityGroups: [], instanceTypes: [] },
      },
      credentialsChanged: jasmine.createSpy('credentialsChanged').and.returnValue({ dirty: {} }),
      regionChanged: jasmine.createSpy('regionChanged').and.returnValue({ dirty: {} }),
      ...overrides,
    };
  }

  beforeEach(() => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: () => null,
      result: Promise.resolve(),
    } as any);
    runtimeServices = {
      cacheInitializer: {},
      loadBalancerReader: { getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails') },
      securityGroupReader: {},
    };
    const configurationService = new AzureServerGroupConfigurationService(Promise, runtimeServices);
    runtimeServices.providerServiceDelegate = {
      getDelegate: jasmine.createSpy('getDelegate').and.returnValue(configurationService),
    };
  });

  function shallowModal(serverGroupCommand: any): any {
    const wrapper = shallow(
      <AzureCloneServerGroupModal
        title="Configure"
        application={application}
        command={serverGroupCommand}
        closeModal={jasmine.createSpy('closeModal')}
        dismissModal={jasmine.createSpy('dismissModal')}
      />,
      { disableLifecycleMethods: true },
    );
    const modal = wrapper.instance() as any;
    modal.context = { services: runtimeServices };
    modal.componentDidMount();
    wrapper.update();
    return wrapper;
  }

  function loadBalancerPage(serverGroupCommand: any): any {
    const page = new ServerGroupLoadBalancers({ formik: formik(serverGroupCommand) } as any) as any;
    page.context = { services: runtimeServices };
    return page;
  }

  function formik(values: any): any {
    return {
      values,
      setFieldValue: jasmine.createSpy('setFieldValue').and.callFake((field: string, value: any) => {
        const path = field.split('.');
        const leaf = path.pop();
        const target = path.reduce((acc: any, key) => {
          acc[key] = acc[key] || {};
          return acc[key];
        }, values);
        target[leaf] = value;
      }),
    };
  }

  it('registers as the Azure clone server group modal', () => {
    registerAzureProvider();

    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.CloneServerGroupModal').render).toBe(
      (RoutedAzureCloneServerGroupModal as any).render,
    );
  });

  it('show opens the React wizard and resolves with the submitted pipeline command', async () => {
    const serverGroupCommand = command();
    const runtimeServices = {} as any;
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve(serverGroupCommand));

    const result = await RoutedAzureCloneServerGroupModal.show(
      {
        title: 'Configure',
        application,
        command: serverGroupCommand,
      } as any,
      runtimeServices,
    );

    expect(result).toBe(serverGroupCommand);
    expect(ReactModal.show).toHaveBeenCalledWith(
      RoutedAzureCloneServerGroupModal,
      jasmine.objectContaining({ title: 'Configure', application, command: serverGroupCommand }),
      { dialogClassName: 'wizard-modal modal-lg' },
      runtimeServices,
    );
  });

  it('cancel and dismiss reject without submitting or mutating the command', async () => {
    const serverGroupCommand = command({ viewState: { mode: 'clone' } });
    const original = JSON.stringify(serverGroupCommand);
    spyOn(ReactModal, 'show').and.returnValue(Promise.reject('cancelled'));

    await expectAsync(
      RoutedAzureCloneServerGroupModal.show(
        { title: 'Clone', application, command: serverGroupCommand } as any,
        {} as any,
      ),
    ).toBeRejectedWith('cancelled');

    expect(JSON.stringify(serverGroupCommand)).toBe(original);
  });

  it('returns a pipeline command with fields consumed by Azure deploy configuration conversion', () => {
    const serverGroupCommand = command();
    const closeModal = jasmine.createSpy('closeModal').and.callFake((result: any) => result);
    const modal = new AzureCloneServerGroupModal({
      title: 'Configure',
      application,
      command: serverGroupCommand,
      closeModal,
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any) as any;

    const submitted = modal.submit(serverGroupCommand);
    const deployConfig = new AzureServerGroupTransformer().convertServerGroupCommandToDeployConfiguration(submitted);

    expect(closeModal).toHaveBeenCalledWith(serverGroupCommand);
    expect(deployConfig.account).toBe('test');
    expect(deployConfig.region).toBe('westus');
    expect(deployConfig.vnet).toBe('vnet-a');
    expect(deployConfig.vnetResourceGroup).toBe('rg-a');
    expect(deployConfig.subnet).toBe('subnet-a');
    expect(deployConfig.loadBalancerName).toBe('lb-a');
    expect(deployConfig.securityGroupName).toBe('sg-a');
    expect(deployConfig.sku.capacity).toBe(2);
    expect(deployConfig.sku.name).toBe('Standard_DS1_v2');
    expect(deployConfig.zones).toEqual(['1']);
  });

  it('renders the React wizard with Azure pages in parity order', () => {
    const serverGroupCommand = command();
    const wrapper = shallowModal(serverGroupCommand);

    wrapper.setState({ loaded: true });
    const wizard = wrapper.find(WizardModal);
    const pages = wizard.prop('render')({
      formik: { values: serverGroupCommand } as any,
      nextIdx: () => 0,
      wizard: {} as any,
    });
    const labels = React.Children.toArray((pages as any).props.children)
      .filter((page: any) => page.type === WizardPage)
      .map((page: any) => page.props.label);

    expect(labels).toEqual([
      'Basic Settings',
      'Image',
      'Instance Type',
      'Capacity and Zones',
      'Load Balancers',
      'Network Settings',
      'Firewalls',
      'Health',
      'Advanced Settings',
      'Tags',
    ]);
  });

  it('renders the deployment strategy selector and forwards strategy changes through Formik without a provider callback', () => {
    const serverGroupCommand = command({
      viewState: { mode: 'createPipeline', disableStrategySelection: false },
    });
    const formikProps = formik(serverGroupCommand);
    const wrapper = shallow(<ServerGroupBasicSettings app={application} formik={formikProps} />);
    const selector = wrapper.find(DeploymentStrategySelector);

    expect(selector.exists()).toBe(true);

    const onFieldChange = selector.exists() ? selector.prop('onFieldChange') : undefined;
    const onSelectorStrategyChange = selector.exists() ? selector.prop('onStrategyChange') : undefined;
    const strategy = { key: 'redblack' } as any;
    onFieldChange?.('scaleDown', true);

    expect(serverGroupCommand.onStrategyChange).toBeUndefined();
    expect(() => onSelectorStrategyChange?.(serverGroupCommand, strategy)).not.toThrow();
    expect(formikProps.setFieldValue).toHaveBeenCalledWith('scaleDown', true);
    expect(formikProps.setFieldValue).toHaveBeenCalledWith('strategy', 'redblack');
  });

  it('renders template selection before configuring deploy-stage commands', () => {
    const serverGroupCommand = { viewState: { requiresTemplateSelection: true, disableStrategySelection: true } };
    const wrapper = shallowModal(serverGroupCommand);

    expect(wrapper.find(DeployInitializer).exists()).toBe(true);
    expect(wrapper.find(WizardModal).exists()).toBe(false);
  });

  it('configures filtered images on the modal working command without mutating the caller command', () => {
    const serverGroupCommand = command({
      images: [
        { imageName: 'ubuntu-west', amis: { westus: ['ami-west'] } },
        { imageName: 'ubuntu-east', amis: { eastus: ['ami-east'] } },
      ],
    });

    const wrapper = shallowModal(serverGroupCommand);
    const workingCommand = (wrapper.state() as any).command;

    expect(workingCommand.backingData.filtered.images).toEqual([{ imageName: 'ubuntu-west', ami: 'ami-west' }]);
    expect(serverGroupCommand.backingData.filtered.images).toBeUndefined();
  });

  it('clears marketplace image selections when the selected region no longer supports them', () => {
    const serverGroupCommand = command({
      imageName: 'ubuntu-west',
      selectedImage: { imageName: 'ubuntu-west', amis: { westus: ['ami-west'] } },
      images: [
        { imageName: 'ubuntu-west', amis: { westus: ['ami-west'] } },
        { imageName: 'ubuntu-east', amis: { eastus: ['ami-east'] } },
      ],
    });
    const wrapper = shallowModal(serverGroupCommand);
    const workingCommand = (wrapper.state() as any).command;

    workingCommand.region = 'eastus';
    const result = workingCommand.regionChanged(workingCommand);
    workingCommand.processCommandUpdateResult(result);

    expect(workingCommand.backingData.filtered.images).toEqual([{ imageName: 'ubuntu-east', ami: 'ami-east' }]);
    expect(workingCommand.imageName).toBeNull();
    expect(workingCommand.selectedImage).toBeNull();
  });

  it('validates custom image fields and keeps them on the command image', () => {
    const serverGroupCommand = command({
      image: { isCustom: true, region: 'westus', imageName: 'custom-image', ostype: 'Linux', uri: 'https://image.vhd' },
      selectedImage: null,
    });
    const page = new ServerGroupImageSettings({ formik: formik(serverGroupCommand) } as any) as any;

    expect(page.validate(serverGroupCommand)).toEqual({});

    page.customImageFieldChanged('uri', 'https://replacement.vhd');

    expect(serverGroupCommand.image).toEqual({
      isCustom: true,
      region: 'westus',
      imageName: 'custom-image',
      ostype: 'Linux',
      uri: 'https://replacement.vhd',
    });
    expect(page.validate({ image: { isCustom: true } })).toEqual({
      imageName: 'Image name required.',
      ostype: 'OS type required.',
      uri: 'URI required.',
    });
  });

  it('renders account and region filtered load balancers from the command', () => {
    spyOn(NetworkReader, 'listNetworks').and.returnValue(Promise.resolve({ azure: [] }) as any);
    const serverGroupCommand = command({
      loadBalancers: ['lb-a'],
      loadBalancerName: null,
      backingData: { loadBalancers: [{ name: 'lb-a', loadBalancerType: 'LOAD_BALANCER' }], filtered: {} },
    });
    const wrapper = shallow(<ServerGroupLoadBalancers formik={formik(serverGroupCommand)} />);

    expect(wrapper.find('option[value="lb-a"]').exists()).toBe(true);
  });

  it('loads VNet and subnet options when an application gateway load balancer is selected', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedVnet: null,
      selectedVnetSubnets: [],
      backingData: {
        loadBalancers: [{ name: 'lb-a', loadBalancerType: 'APPLICATION_GATEWAY' }],
        filtered: { loadBalancers: ['lb-a'] },
      },
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: () => Promise.resolve([{ vnet: 'vnet-a' }]),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          {
            account: 'test',
            name: 'vnet-a',
            region: 'westus',
            resourceGroup: 'rg-a',
            subnets: [
              { name: 'subnet-a', devices: [] },
              { name: 'gateway-subnet', devices: [{ type: 'applicationGateways' }] },
            ],
          },
        ],
      } as any),
    );

    const page = loadBalancerPage(serverGroupCommand);
    await page.loadBalancerChanged('lb-a');

    expect(serverGroupCommand.loadBalancerType).toBe('Azure Application Gateway');
    expect(serverGroupCommand.selectedVnet.name).toBe('vnet-a');
    expect(serverGroupCommand.selectedVnetSubnets).toEqual(['subnet-a']);
  });

  it('clears stale subnet selections after refreshing load balancer VNet subnets', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedSubnet: 'old-subnet',
      subnet: 'old-subnet',
      backingData: {
        loadBalancers: [{ name: 'lb-a', loadBalancerType: 'APPLICATION_GATEWAY' }],
        filtered: { loadBalancers: ['lb-a'] },
      },
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: () => Promise.resolve([{ vnet: 'vnet-a' }]),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          {
            account: 'test',
            name: 'vnet-a',
            region: 'westus',
            resourceGroup: 'rg-a',
            subnets: [{ name: 'subnet-new', devices: [] }],
          },
        ],
      } as any),
    );

    const page = loadBalancerPage(serverGroupCommand);
    await page.loadBalancerChanged('lb-a');

    expect(serverGroupCommand.selectedVnet.name).toBe('vnet-a');
    expect(serverGroupCommand.selectedVnetSubnets).toEqual(['subnet-new']);
    expect(serverGroupCommand.selectedSubnet).toBeNull();
    expect(serverGroupCommand.subnet).toBeNull();
  });

  it('uses refreshed VNet metadata for existing VNet selections', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a', subnets: [{ name: 'old-subnet', devices: [] }] },
      selectedSubnet: 'old-subnet',
      subnet: 'old-subnet',
      selectedVnetSubnets: ['old-subnet'],
      backingData: { loadBalancers: [], filtered: { loadBalancers: [] } },
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails'),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          {
            account: 'test',
            name: 'vnet-a',
            region: 'westus',
            resourceGroup: 'rg-a',
            subnets: [{ name: 'subnet-new', devices: [] }],
          },
        ],
      } as any),
    );

    const page = loadBalancerPage(serverGroupCommand);
    await page.loadVnetSubnets(null, null, 0);

    expect(serverGroupCommand.selectedVnet.subnets).toEqual([{ name: 'subnet-new', devices: [] }]);
    expect(serverGroupCommand.selectedVnetSubnets).toEqual(['subnet-new']);
    expect(serverGroupCommand.selectedSubnet).toBeNull();
    expect(serverGroupCommand.subnet).toBeNull();
  });

  it('loads VNet and subnet options on mount when no load balancer is selected', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedVnet: null,
      selectedVnetSubnets: [],
      backingData: { loadBalancers: [], filtered: { loadBalancers: [] } },
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails'),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          {
            account: 'test',
            name: 'vnet-a',
            region: 'westus',
            resourceGroup: 'rg-a',
            subnets: [{ name: 'subnet-a', devices: [] }],
          },
        ],
      } as any),
    );

    const page = loadBalancerPage(serverGroupCommand);
    page.componentDidMount();
    await Promise.resolve();
    await Promise.resolve();

    expect(NetworkReader.listNetworks).toHaveBeenCalled();
    expect(serverGroupCommand.allVnets).toEqual([
      {
        account: 'test',
        name: 'vnet-a',
        region: 'westus',
        resourceGroup: 'rg-a',
        subnets: [{ name: 'subnet-a', devices: [] }],
      },
    ]);
    expect(serverGroupCommand.selectedVnetSubnets).toEqual(['subnet-a']);
  });

  it('requires virtual network and subnet selections when network settings are configured', () => {
    const page = new ServerGroupNetworkSettings({ formik: formik(command()) } as any) as any;

    expect(
      page.validate({
        loadBalancerType: 'Azure Load Balancer',
        selectedSubnet: null,
        selectedVnet: null,
        subnet: null,
        viewState: { networkSettingsConfigured: true },
      }),
    ).toEqual({
      selectedSubnet: 'Subnet required.',
      selectedVnet: 'Virtual network required.',
    });

    expect(
      page.validate({
        loadBalancerType: null,
        selectedSubnet: null,
        selectedVnet: null,
        subnet: null,
        viewState: { networkSettingsConfigured: true },
      }),
    ).toEqual({
      selectedSubnet: 'Subnet required.',
      selectedVnet: 'Virtual network required.',
    });

    expect(
      page.validate({
        loadBalancerType: 'Azure Load Balancer',
        selectedVnet: null,
        subnet: 'subnet-a',
        vnet: 'vnet-a',
        viewState: { networkSettingsConfigured: true },
      }),
    ).toEqual({ selectedVnet: 'Virtual network required.' });
  });

  it('requires subnet and load-balancer-supplied VNet for application gateway commands', () => {
    const page = new ServerGroupNetworkSettings({ formik: formik(command()) } as any) as any;

    expect(
      page.validate({
        loadBalancerType: 'Azure Application Gateway',
        selectedSubnet: null,
        selectedVnet: null,
        subnet: null,
        viewState: { networkSettingsConfigured: true },
      }),
    ).toEqual({
      selectedSubnet: 'Subnet required.',
      selectedVnet: 'Virtual network required.',
    });

    expect(
      page.validate({
        loadBalancerType: 'Azure Application Gateway',
        selectedSubnet: 'subnet-a',
        selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a' },
        subnet: null,
        viewState: { networkSettingsConfigured: true },
      }),
    ).toEqual({ selectedSubnet: 'Subnet required.' });

    expect(
      page.validate({
        loadBalancerType: 'Azure Application Gateway',
        selectedSubnet: 'subnet-a',
        selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a' },
        subnet: 'subnet-a',
        viewState: { networkSettingsConfigured: true },
      }),
    ).toEqual({});
  });

  it('handles VNet and subnet loading failures without throwing', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedVnet: null,
      selectedVnetSubnets: [],
      backingData: { loadBalancers: [], filtered: { loadBalancers: [] } },
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails'),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(Promise.reject(new Error('boom')));

    const page = loadBalancerPage(serverGroupCommand);

    await expectAsync(page.loadVnetSubnets(null, null, 0)).toBeResolved();

    expect(serverGroupCommand.selectedVnetSubnets).toEqual([]);
  });

  it('clears stale network selections when the load balancer changes', async () => {
    const serverGroupCommand = command({
      backingData: {
        loadBalancers: [{ name: 'lb-b', loadBalancerType: 'APPLICATION_GATEWAY' }],
        filtered: { loadBalancers: ['lb-b'] },
      },
      selectedVnet: { name: 'old-vnet', resourceGroup: 'old-rg' },
      selectedVnetSubnets: ['old-subnet'],
      selectedSubnet: 'old-subnet',
      subnet: 'old-subnet',
      vnet: 'old-vnet',
      vnetResourceGroup: 'old-rg',
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails'),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(Promise.reject(new Error('boom')));

    const page = loadBalancerPage(serverGroupCommand);

    await page.loadBalancerChanged('lb-b', true);

    expect(serverGroupCommand.selectedVnet).toBeNull();
    expect(serverGroupCommand.vnet).toBeNull();
    expect(serverGroupCommand.vnetResourceGroup).toBeNull();
    expect(serverGroupCommand.selectedSubnet).toBeNull();
    expect(serverGroupCommand.subnet).toBeNull();
    expect(serverGroupCommand.selectedVnetSubnets).toEqual([]);
  });

  it('uses the account and region matched load balancer metadata when names collide', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedVnet: { name: 'vnet-a', resourceGroup: 'rg-a' },
      selectedVnetSubnets: [],
      backingData: {
        loadBalancers: [
          { name: 'lb-a', account: 'other', region: 'eastus', loadBalancerType: 'APPLICATION_GATEWAY' },
          { name: 'lb-a', account: 'test', region: 'westus', loadBalancerType: 'LOAD_BALANCER' },
        ],
        filtered: { loadBalancers: ['lb-a'] },
      },
    });
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: () => Promise.resolve([]),
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          { account: 'test', name: 'vnet-a', region: 'westus', resourceGroup: 'rg-a', subnets: [{ name: 'subnet-a' }] },
        ],
      } as any),
    );

    const page = loadBalancerPage(serverGroupCommand);
    await page.loadBalancerChanged('lb-a');

    expect(serverGroupCommand.loadBalancerType).toBe('Azure Load Balancer');
    expect(serverGroupCommand.selectedVnet.name).toBe('vnet-a');
  });

  it('ignores stale load balancer subnet responses', async () => {
    const serverGroupCommand = command({
      loadBalancerName: null,
      loadBalancerType: null,
      selectedVnet: null,
      selectedVnetSubnets: [],
      backingData: {
        loadBalancers: [
          { name: 'lb-a', loadBalancerType: 'APPLICATION_GATEWAY' },
          { name: 'lb-b', loadBalancerType: 'APPLICATION_GATEWAY' },
        ],
        filtered: { loadBalancers: ['lb-a', 'lb-b'] },
      },
    });
    let resolveFirstRequest: (details: any[]) => void;
    runtimeServices.loadBalancerReader = {
      getLoadBalancerDetails: (_provider: string, _account: string, _region: string, loadBalancerName: string) => {
        if (loadBalancerName === 'lb-a') {
          return new Promise((resolve) => {
            resolveFirstRequest = resolve;
          });
        }
        return Promise.resolve([{ vnet: 'vnet-b' }]);
      },
    };
    spyOn(NetworkReader, 'listNetworks').and.returnValue(
      Promise.resolve({
        azure: [
          { account: 'test', name: 'vnet-a', region: 'westus', resourceGroup: 'rg-a', subnets: [{ name: 'subnet-a' }] },
          { account: 'test', name: 'vnet-b', region: 'westus', resourceGroup: 'rg-b', subnets: [{ name: 'subnet-b' }] },
        ],
      } as any),
    );

    const page = loadBalancerPage(serverGroupCommand);
    const firstRequest = page.loadBalancerChanged('lb-a');
    const secondRequest = page.loadBalancerChanged('lb-b');
    await secondRequest;
    resolveFirstRequest([{ vnet: 'vnet-a' }]);
    await firstRequest;

    expect(serverGroupCommand.loadBalancerName).toBe('lb-b');
    expect(serverGroupCommand.selectedVnet.name).toBe('vnet-b');
    expect(serverGroupCommand.selectedVnetSubnets).toEqual(['subnet-b']);
  });

  it('renders health settings protocol, port, and HTTP path fields', () => {
    const wrapper = shallow(
      <ServerGroupHealthSettings
        formik={formik({ healthSettings: { protocol: 'http', port: '80', requestPath: '/health' } })}
      />,
    );

    expect(wrapper.find('select').exists()).toBe(true);
    expect(wrapper.find('input[value="80"]').exists()).toBe(true);
    expect(wrapper.find('input[value="/health"]').exists()).toBe(true);
  });

  it('adds Azure data disks with the legacy defaults', () => {
    const serverGroupCommand = command({
      dataDisks: [],
      backingData: { dataDiskTypes: ['Standard_LRS'], dataDiskCachingTypes: ['None'], filtered: {} },
    });
    const page = new ServerGroupAdvancedSettings({ formik: formik(serverGroupCommand) } as any) as any;

    page.addDataDisk();

    expect(serverGroupCommand.dataDisks).toEqual([
      {
        lun: 0,
        managedDisk: { storageAccountType: 'Standard_LRS' },
        diskSizeGB: 1,
        caching: 'None',
        createOption: 'Empty',
      },
    ]);
  });

  it('uses the shared map editor for Azure tags', () => {
    const wrapper = shallow(<ServerGroupTags formik={formik({ instanceTags: { team: 'cd' } })} />);

    expect(wrapper.find(MapEditor).exists()).toBe(true);
  });
});
