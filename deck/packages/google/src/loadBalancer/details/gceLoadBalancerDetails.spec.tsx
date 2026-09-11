import { mount, shallow } from 'enzyme';
import React from 'react';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  HelpField,
  InfrastructureCaches,
  ManagedMenuItem,
  TaskExecutor,
} from '@spinnaker/core';

import { GceLoadBalancerChoiceModal } from '../configure/choice/GceLoadBalancerChoiceModal';
import {
  GceLoadBalancerActions,
  GceLoadBalancerListenersSection,
  loadGceLoadBalancerDetails,
} from './gceLoadBalancerDetails';

describe('GceLoadBalancerActions', () => {
  const app = { name: 'fnord' } as any;
  const loadBalancer = {
    account: 'account-a',
    instances: [],
    loadBalancerType: 'INTERNAL_MANAGED',
    name: 'fnord-main',
    region: 'europe-west1',
  } as any;

  it('hides write actions when the Google provider is disabled', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(true);

    const wrapper = shallow(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);

    expect(wrapper.isEmptyRender()).toBe(true);
  });

  it('opens the current load balancer in edit mode through managed-resource gating', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(false);
    const show = spyOn(GceLoadBalancerChoiceModal, 'show').and.returnValue(Promise.resolve() as any);

    const wrapper = shallow(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
    const edit = wrapper.find(ManagedMenuItem).filterWhere((item) => item.prop('children') === 'Edit Load Balancer');

    expect(edit.prop('application')).toBe(app);
    expect(edit.prop('resource')).toBe(loadBalancer);
    edit.prop('onClick')();
    expect(show).toHaveBeenCalledOnceWith({
      app,
      application: app,
      forPipelineConfig: false,
      isNew: false,
      loadBalancer,
      mode: 'edit',
    } as any);
  });

  it('keeps delete behind managed-resource gating and disables it while instances are attached', () => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(false);

    const editable = shallow(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
    const managedDelete = editable
      .find(ManagedMenuItem)
      .filterWhere((item) => item.prop('children') === 'Delete Load Balancer');
    expect(managedDelete.prop('application')).toBe(app);
    expect(managedDelete.prop('resource')).toBe(loadBalancer);

    const attached = shallow(
      <GceLoadBalancerActions app={app} loadBalancer={{ ...loadBalancer, instances: [{ name: 'instance-a' }] }} />,
    );
    expect(
      attached.find(ManagedMenuItem).filterWhere((item) => item.prop('children') === 'Delete Load Balancer'),
    ).toHaveSize(0);
    expect(attached.find('li.disabled').text()).toContain('Delete Load Balancer');
  });
});

describe('loadGceLoadBalancerDetails', () => {
  it('matches scoped regional HTTP load balancers by raw urlMapName and loadBalancerType', async () => {
    const normalizedLoadBalancer = {
      account: 'test',
      defaultService: { healthCheck: { name: 'hc-default' }, name: 'backend-default' },
      hostRules: [],
      listeners: [{ name: 'regional-listener' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-url-map (test/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'regional-url-map',
    };
    const loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails').and.returnValue(
        Promise.resolve([
          {
            dnsname: '1.2.3.4',
            listenerDescriptions: [{ listener: { loadBalancerPort: '443' } }],
          },
        ]),
      ),
    };
    const accountService = {
      getAccountDetails: jasmine
        .createSpy('getAccountDetails')
        .and.returnValue(Promise.resolve({ project: 'gce-project' })),
    };
    const autoClose = jasmine.createSpy('autoClose');

    const loadBalancer = await loadGceLoadBalancerDetails({
      app: { loadBalancers: { data: [normalizedLoadBalancer] } } as any,
      autoClose,
      loadBalancerParams: {
        accountId: 'test',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'regional-url-map',
        provider: 'gce',
        region: 'us-central1',
        vpcId: null,
      },
      loadBalancerReader: loadBalancerReader as any,
      accountService: accountService as any,
    });

    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'gce',
      'test',
      'us-central1',
      'regional-listener',
    );
    expect(loadBalancer).toBe(normalizedLoadBalancer as any);
    expect((loadBalancer as any).logsLink).toContain('regional-url-map');
    expect((loadBalancer as any).logsLink).not.toContain('(test/us-central1/EXTERNAL_MANAGED)');
  });

  it('rejects same-name regional HTTP families when loadBalancerType does not match', async () => {
    const autoClose = jasmine.createSpy('autoClose');
    const loadBalancerReader = {
      getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails'),
    };

    await loadGceLoadBalancerDetails({
      app: {
        loadBalancers: {
          data: [
            {
              account: 'test',
              listeners: [{ name: 'internal-listener' }],
              loadBalancerType: 'INTERNAL_MANAGED',
              name: 'shared-map (test/us-central1/INTERNAL_MANAGED)',
              provider: 'gce',
              region: 'us-central1',
              urlMapName: 'shared-map',
            },
          ],
        },
      } as any,
      autoClose,
      loadBalancerParams: {
        accountId: 'test',
        loadBalancerType: 'EXTERNAL_MANAGED',
        name: 'shared-map',
        provider: 'gce',
        region: 'us-central1',
        vpcId: null,
      },
      loadBalancerReader: loadBalancerReader as any,
    });

    expect(autoClose).toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).not.toHaveBeenCalled();
  });
});

describe('GceLoadBalancerActions delete behavior', () => {
  const app = { name: 'fnord' } as any;

  beforeEach(() => {
    spyOn(CloudProviderRegistry, 'isDisabled').and.returnValue(false);
  });

  it('deletes EXTERNAL_MANAGED load balancers using raw listener names and regional scope', async () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve({}) as any);
    spyOn(InfrastructureCaches, 'clearCache');
    const executeTaskSpy = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}) as any);
    const loadBalancer = {
      account: 'test-account',
      instances: [],
      listeners: [{ name: 'regional-listener-443' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-url-map (test-account/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'regional-url-map',
    };

    const wrapper = mount(<GceLoadBalancerActions app={app} loadBalancer={loadBalancer} />);
    wrapper
      .find(ManagedMenuItem)
      .filterWhere((item) => item.prop('children') === 'Delete Load Balancer')
      .prop('onClick')();
    const modalParams = confirmSpy.calls.mostRecent().args[0] as any;
    await modalParams.submitMethod({ deleteHealthChecks: true });

    expect(executeTaskSpy).toHaveBeenCalledWith({
      application: app,
      description: 'Delete load balancer: regional-url-map in test-account:us-central1',
      job: [
        jasmine.objectContaining({
          cloudProvider: 'gce',
          credentials: 'test-account',
          deleteHealthChecks: true,
          loadBalancerName: 'regional-listener-443',
          loadBalancerType: 'EXTERNAL_MANAGED',
          region: 'us-central1',
          regions: ['us-central1'],
          type: 'deleteLoadBalancer',
        }),
      ],
    });
    wrapper.unmount();
  });

  it('shows delete-health-check controls for EXTERNAL_MANAGED and REGIONAL_EXTERNAL_NETWORK load balancers', () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve({}) as any);

    const externalManaged = {
      account: 'test-account',
      instances: [],
      listeners: [{ name: 'regional-listener' }],
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-url-map (test-account/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      urlMapName: 'regional-url-map',
    };
    mount(<GceLoadBalancerActions app={app} loadBalancer={externalManaged} />)
      .find(ManagedMenuItem)
      .filterWhere((item) => item.prop('children') === 'Delete Load Balancer')
      .prop('onClick')();
    expect(shallow(confirmSpy.calls.all()[0].args[0].bodyContent).find('input[type="checkbox"]').exists()).toBe(true);

    const regionalExternalNetwork = {
      account: 'test-account',
      backendService: { healthCheck: { name: 'network-hc' } },
      instances: [],
      loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
      name: 'regional-network-lb',
      provider: 'gce',
      region: 'us-central1',
    };
    mount(<GceLoadBalancerActions app={app} loadBalancer={regionalExternalNetwork} />)
      .find(ManagedMenuItem)
      .filterWhere((item) => item.prop('children') === 'Delete Load Balancer')
      .prop('onClick')();
    expect(shallow(confirmSpy.calls.all()[1].args[0].bodyContent).find('input[type="checkbox"]').exists()).toBe(true);
  });
});

describe('GceLoadBalancerListenersSection', () => {
  const app = { name: 'fnord' } as any;

  function renderListeners(loadBalancer: any) {
    return shallow(<GceLoadBalancerListenersSection app={app} loadBalancer={loadBalancer} />);
  }

  it('renders NETWORK listenerDescriptions with load balancer and instance protocol/port mapping', () => {
    const wrapper = renderListeners({
      loadBalancerType: 'NETWORK',
      listeners: [{ port: '8080' }],
      elb: {
        listenerDescriptions: [
          {
            listener: {
              instancePort: '8080',
              instanceProtocol: 'TCP',
              loadBalancerPort: '8080',
              protocol: 'TCP',
            },
          },
        ],
      },
    });

    expect(wrapper.find('dd').text()).toBe('TCP:8080 → TCP:8080');
    expect(wrapper.find('li').exists()).toBe(false);
  });

  it('renders TCP and SSL listenerDescriptions with load balancer and instance protocol/port mapping', () => {
    const tcp = renderListeners({
      loadBalancerType: 'TCP',
      elb: {
        listenerDescriptions: [
          {
            listener: {
              instancePort: '8443',
              instanceProtocol: 'TCP',
              loadBalancerPort: '443',
              protocol: 'TCP',
            },
          },
        ],
      },
    });
    const ssl = renderListeners({
      loadBalancerType: 'SSL',
      elb: {
        listenerDescriptions: [
          {
            listener: {
              instancePort: '8443',
              instanceProtocol: 'TCP',
              loadBalancerPort: '443',
              protocol: 'SSL',
            },
          },
        ],
      },
    });

    expect(tcp.find('dd').text()).toBe('TCP:443 → TCP:8443');
    expect(ssl.find('dd').text()).toBe('SSL:443 → TCP:8443');
    expect(tcp.find(HelpField).prop('id')).toBe('gce.httpLoadBalancer.namedPort');
    expect(ssl.find(HelpField).prop('id')).toBe('gce.httpLoadBalancer.namedPort');
  });

  it('prefers HTTP-family elb.listenerDescriptions over normalized listeners', () => {
    const wrapper = renderListeners({
      loadBalancerType: 'EXTERNAL_MANAGED',
      listeners: [{ port: '80' }, { port: '443' }],
      provider: 'gce',
      elb: {
        listenerDescriptions: [
          {
            listener: {
              instancePort: '8080',
              instanceProtocol: 'HTTP',
              loadBalancerPort: '443',
              protocol: 'HTTPS',
            },
          },
        ],
      },
    });

    expect(wrapper.find('dd')).toHaveSize(1);
    expect(wrapper.find('dd').text()).toBe('HTTPS:443 → HTTP:8080');
    expect(wrapper.find(HelpField).prop('id')).toBe('gce.httpLoadBalancer.namedPort');
  });

  it('falls back to normalized HTTP listeners only when elb.listenerDescriptions are absent', () => {
    const wrapper = renderListeners({
      loadBalancerType: 'HTTP',
      listeners: [
        { port: '80', name: 'frontend-80' },
        { port: '443', certificate: 'projects/p/certificates/cert', name: 'frontend-443' },
      ],
      provider: 'gce',
    });

    expect(wrapper.find('dd').map((node) => node.text())).toEqual(['HTTP:80', 'HTTPS:443']);
  });

  it('shows no listeners configured when neither descriptions nor HTTP fallback listeners exist', () => {
    const wrapper = renderListeners({
      loadBalancerType: 'NETWORK',
      listeners: [{ port: '8080' }],
    });

    expect(wrapper.find('span').text()).toBe('No listeners configured');
    expect(wrapper.find('dd').exists()).toBe(false);
  });
});
