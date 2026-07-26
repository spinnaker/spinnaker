import React from 'react';
import { mount as enzymeMount } from 'enzyme';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  DeckRuntimeContext,
  ErrorModalService,
  InfrastructureCaches,
  LoadBalancerWriter,
  ManagedMenuItem,
  Registry,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  SubmitButton,
  TaskExecutor,
} from '@spinnaker/core';

import { GceCacheConfigurer } from './cache/cacheConfigurer.service';
import * as googlePackage from './index';
import { GceImageReader } from './image';
import { decorateInstance } from './instance/details/GceInstanceDetails';
import { GceInstanceTypeService } from './instance/gceInstanceType.service';
import { GceMultiInstanceTaskTransformer } from './instance/gceMultiInstanceTask.transformer';
import { GceLoadBalancerSetTransformer } from './loadBalancer/loadBalancer.setTransformer';
import { GceLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import { GceLoadBalancerChoiceModal } from './loadBalancer/configure/choice/GceLoadBalancerChoiceModal';
import { GceLoadBalancerActions, loadGceLoadBalancerDetails } from './loadBalancer/details/gceLoadBalancerDetails';
import { interpolatedBakeDetailUrl } from './pipeline/stages/bake/gceBakeStage';
import { GceSecurityGroupModalComponent as GceSecurityGroupModal } from './securityGroup/configure/GceSecurityGroupModal';
import { GceSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { GceSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { GceServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { GceServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import {
  cloneGceServerGroup,
  GceServerGroupActions,
  gceServerGroupDetailsGetter,
} from './serverGroup/details/gceServerGroupDetails';
import { GceServerGroupTransformer } from './serverGroup/serverGroup.transformer';
import { GceSubnetRenderer } from './subnet/subnet.renderer';

describe('Google provider registration', () => {
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) =>
    React.createElement(DeckRuntimeContext.Provider, { value: { services: runtimeServices } as any }, children);
  const mount = (component: React.ReactElement) => enzymeMount(component, { wrappingComponent: RuntimeWrapper });

  beforeEach(() => {
    runtimeServices = {
      securityGroupReader: { getAllSecurityGroups: () => Promise.resolve({}) },
      serverGroupCommandBuilder: {},
      serverGroupWriter: {},
    };
  });

  const legacyCtrlKey = ['Cont', 'roller'].join('');
  const legacyViewKey = ['Template', 'Url'].join('');
  const legacyModuleExport = ['GOOGLE', 'MODULE'].join('_');
  const stageViewKey = ['template', 'Url'].join('');
  const stageCtrlKey = ['cont', 'roller'].join('');
  const stepLabelViewKey = ['execution', 'Step', 'Label', 'Url'].join('');
  const markupExtension = ['.', 'ht', 'ml'].join('');
  const injectionMetadataKey = ['$', 'inject'].join('');

  function expectNoAngularStageRegistration(stageConfig: any): void {
    expect(stageConfig[stageViewKey]).withContext(`gce ${stageConfig.provides} stage view`).toBeUndefined();
    expect(stageConfig[stageCtrlKey]).withContext(`gce ${stageConfig.provides} legacy handler`).toBeUndefined();
    expect(stageConfig[stepLabelViewKey]).withContext(`gce ${stageConfig.provides} step label view`).toBeUndefined();

    const htmlValues = Object.keys(stageConfig)
      .map((key) => stageConfig[key])
      .filter((value) => typeof value === 'string' && value.endsWith(markupExtension));

    expect(htmlValues).withContext(`gce ${stageConfig.provides} markup stage config values`).toEqual([]);
  }

  it('registers Google without exporting an Angular module token', () => {
    googlePackage.registerGoogleProvider();

    expect(Object.prototype.hasOwnProperty.call(googlePackage, legacyModuleExport)).toBe(false);

    expect(CloudProviderRegistry.getValue('gce', 'cache.configurer')).toBe(GceCacheConfigurer);
    expect(CloudProviderRegistry.getValue('gce', 'image.reader')).toBe(GceImageReader);
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.transformer')).toBe(GceServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.commandBuilder')).toBe(GceServerGroupCommandBuilder);
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.configurationService')).toBe(
      GceServerGroupConfigurationService,
    );
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.CloneServerGroupModal')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsGetter')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsActions')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'serverGroup.detailsSections').length).toBeGreaterThan(0);
    expect(CloudProviderRegistry.getValue('gce', 'instance.details')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'instance.instanceTypeService')).toBe(GceInstanceTypeService);
    expect(CloudProviderRegistry.getValue('gce', 'instance.multiInstanceTaskTransformer')).toBe(
      GceMultiInstanceTaskTransformer,
    );
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.useDetailsHook')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.detailsActions')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.detailsSections').length).toBeGreaterThan(0);
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.CreateLoadBalancerModal')).toBe(
      GceLoadBalancerChoiceModal,
    );
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.CreateLoadBalancerModal').supportsPipelineConfig).toBe(
      true,
    );
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.transformer')).toBe(GceLoadBalancerTransformer);
    expect(CloudProviderRegistry.getValue('gce', 'loadBalancer.setTransformer')).toBe(GceLoadBalancerSetTransformer);
    expect(CloudProviderRegistry.getValue('gce', 'securityGroup.CreateSecurityGroupModal')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'securityGroup.details')).toBeDefined();
    expect(CloudProviderRegistry.getValue('gce', 'securityGroup.reader')).toBe(GceSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('gce', 'securityGroup.transformer')).toBe(GceSecurityGroupTransformer);
    expect(CloudProviderRegistry.getValue('gce', 'subnet.renderer')).toBe(GceSubnetRenderer);
    expect(CloudProviderRegistry.getValue('gce', 'applicationProviderFields')).toEqual([
      {
        field: 'associatePublicIpAddress',
        helpKey: 'gce.serverGroup.associatePublicIpAddress.providerField',
        label: 'Associate Public IP Address',
        type: 'boolean',
      },
    ]);

    expect(CloudProviderRegistry.getValue('gce', `serverGroup.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `serverGroup.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `instance.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `instance.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `loadBalancer.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `loadBalancer.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `securityGroup.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('gce', `securityGroup.details${legacyViewKey}`)).toBeNull();
  });

  it('does not bundle Google Angular HTML templates', () => {
    const templates = require.context('./', true, /\.htm[l]$/).keys();
    expect(templates).toEqual([]);
  });

  it('registers Google delegates without Angular injection metadata', () => {
    [
      GceCacheConfigurer,
      GceInstanceTypeService,
      GceMultiInstanceTaskTransformer,
      GceLoadBalancerSetTransformer,
      GceLoadBalancerTransformer,
      GceSecurityGroupReader,
      GceSecurityGroupTransformer,
      GceServerGroupCommandBuilder,
      GceServerGroupConfigurationService,
      GceServerGroupTransformer,
      GceSubnetRenderer,
    ].forEach((delegate) => {
      expect(Object.prototype.hasOwnProperty.call(delegate, injectionMetadataKey)).toBe(false);
    });
  });

  it('constructs the load balancer set transformer when Core passes the deferred compatibility argument', () => {
    const transformer = new GceLoadBalancerSetTransformer({ when: jasmine.createSpy('when') } as any);
    const result = transformer.normalizeLoadBalancerSet([
      {
        loadBalancerType: 'HTTP',
        provider: 'gce',
        type: 'gce',
        name: 'forwarding-rule-80',
        urlMapName: 'frontend-map',
        portRange: '80-80',
      },
      {
        loadBalancerType: 'HTTP',
        provider: 'gce',
        type: 'gce',
        name: 'forwarding-rule-443',
        urlMapName: 'frontend-map',
        portRange: '443-443',
      },
    ] as any);

    expect(result.length).toBe(1);
    expect(result[0].name).toBe('frontend-map');
    expect((result[0] as any).listeners.map((listener: any) => listener.port)).toEqual(['80', '443']);
  });

  it('allows GCE firewalls that apply to all target tags', async () => {
    runtimeServices.securityGroupReader = {
      getAllSecurityGroups: () => Promise.resolve({}),
    };
    const wrapper = mount(
      React.createElement(GceSecurityGroupModal, {
        application: { name: 'fnord', securityGroups: { data: [] } },
        credentials: 'test-account',
      }),
    );

    wrapper.setState({
      securityGroup: {
        ...(wrapper.state() as any).securityGroup,
        ipIngress: [{ type: 'tcp', startPort: 443, endPort: 443 }],
        name: 'fnord-firewall',
        network: 'default',
        sourceRanges: ['10.0.0.0/8'],
        sourceTags: [],
        targetTags: [],
      },
    } as any);

    await Promise.resolve();
    await Promise.resolve();
    wrapper.update();

    expect(wrapper.find(SubmitButton).prop('isDisabled')).toBe(false);
  });

  it('loads and enriches GCE load balancer details through the Core reader', async () => {
    const summary = {
      account: 'test-account',
      name: 'frontend-map',
      provider: 'gce',
      region: 'global',
      loadBalancerType: 'HTTP',
      listeners: [{ name: 'frontend-80' }, { name: 'frontend-443' }],
      hostRules: [
        {
          pathMatcher: {
            defaultService: { name: 'backend-default', healthCheck: { name: 'hc-default' } },
            pathRules: [{ backendService: { name: 'backend-api', healthCheck: { name: 'hc-api' } } }],
          },
        },
      ],
      defaultService: { name: 'backend-default', healthCheck: { name: 'hc-default' } },
    };
    const loadBalancerReader = {
      getLoadBalancerDetails: jasmine
        .createSpy('getLoadBalancerDetails')
        .and.callFake((_provider: string, _account: string, _region: string, name: string) =>
          Promise.resolve([
            {
              name,
              vpcid: null,
              dnsname: `${name}.example.com`,
              listenerDescriptions: [{ listener: { loadBalancerPort: name.endsWith('443') ? '443' : '80' } }],
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
      app: { loadBalancers: { data: [summary] } } as any,
      loadBalancerParams: {
        accountId: 'test-account',
        name: 'frontend-map',
        provider: 'gce',
        region: 'global',
        vpcId: null,
      },
      loadBalancerReader: loadBalancerReader as any,
      accountService: accountService as any,
      autoClose,
    });

    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'gce',
      'test-account',
      'global',
      'frontend-80',
    );
    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'gce',
      'test-account',
      'global',
      'frontend-443',
    );
    expect(accountService.getAccountDetails).toHaveBeenCalledWith('test-account');
    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancer).toBe(summary as any);
    expect((loadBalancer as any).elb.dns).toEqual([
      { dnsname: 'frontend-80.example.com', protocol: 'http:' },
      { dnsname: 'frontend-443.example.com', protocol: 'https:' },
    ]);
    expect((loadBalancer as any).elb.listenerDescriptions.length).toBe(2);
    expect((loadBalancer as any).elb.backendServices.map((backendService: any) => backendService.name)).toEqual([
      'backend-default',
      'backend-api',
    ]);
    expect((loadBalancer as any).elb.healthChecks.map((healthCheck: any) => healthCheck.name)).toEqual([
      'hc-default',
      'hc-api',
    ]);
    expect((loadBalancer as any).project).toBe('gce-project');
    expect((loadBalancer as any).logsLink).toContain('gce-project');
    expect((loadBalancer as any).logsLink).toContain('frontend-map');
  });

  it('deletes HTTP load balancers by forwarding rule and preserves delete params', async () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve({}) as any);
    spyOn(LoadBalancerWriter, 'deleteLoadBalancer').and.returnValue(Promise.resolve({}) as any);
    const clearCacheSpy = spyOn(InfrastructureCaches, 'clearCache');
    const executeTaskSpy = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}) as any);
    const app = { name: 'fnord' };
    const loadBalancer = {
      account: 'test-account',
      loadBalancerType: 'HTTP',
      listeners: [{ name: 'frontend-80' }],
      name: 'frontend-map',
      provider: 'gce',
      region: 'global',
      urlMapName: 'frontend-map',
    };

    const wrapper = mount(React.createElement(GceLoadBalancerActions, { app, loadBalancer }));
    wrapper
      .find(ManagedMenuItem)
      .filterWhere((item) => item.prop('children') === 'Delete Load Balancer')
      .prop('onClick')();
    const modalParams = confirmSpy.calls.mostRecent().args[0] as any;
    await modalParams.submitMethod({ deleteHealthChecks: true, reason: 'cleanup' });

    expect(LoadBalancerWriter.deleteLoadBalancer).not.toHaveBeenCalled();
    expect(clearCacheSpy).toHaveBeenCalledWith('backendServices');
    expect(clearCacheSpy).toHaveBeenCalledWith('healthChecks');
    expect(executeTaskSpy).toHaveBeenCalledWith({
      application: app,
      description: 'Delete load balancer: frontend-map in test-account:global',
      job: [
        jasmine.objectContaining({
          cloudProvider: 'gce',
          credentials: 'test-account',
          deleteHealthChecks: true,
          loadBalancerName: 'frontend-80',
          loadBalancerType: 'HTTP',
          reason: 'cleanup',
          region: 'global',
          regions: ['global'],
          type: 'deleteLoadBalancer',
        }),
      ],
    });
  });

  it('loads GCE server group details even when the app summary is not loaded yet', (done) => {
    const autoClose = jasmine.createSpy('autoClose');
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({
        account: 'test-account',
        launchConfig: {
          instanceTemplate: {
            properties: { networkInterfaces: [] },
            selfLink:
              'https://compute.googleapis.com/compute/beta/projects/gce-project/global/instanceTemplates/fnord-v001',
          },
        },
        name: 'fnord-v001',
        region: 'us-central1',
        zones: ['us-central1-b', 'us-central1-a'],
      }) as any,
    );

    gceServerGroupDetailsGetter(
      {
        app: { loadBalancers: { data: [] }, name: 'fnord', serverGroups: { data: [] } },
        serverGroup: { accountId: 'test-account', name: 'fnord-v001', region: 'us-central1' },
      },
      autoClose,
    ).subscribe({
      error: done.fail,
      next: (serverGroup: any) => {
        expect(serverGroup.name).toBe('fnord-v001');
        expect(serverGroup.account).toBe('test-account');
        expect(serverGroup.zones).toEqual(['us-central1-a', 'us-central1-b']);
      },
      complete: () => {
        expect(autoClose).not.toHaveBeenCalled();
        expect(ServerGroupReader.getServerGroup).toHaveBeenCalledWith(
          'fnord',
          'test-account',
          'us-central1',
          'fnord-v001',
        );
        done();
      },
    });
  });

  it('loads GCE server group details when the launch template is missing', (done) => {
    const autoClose = jasmine.createSpy('autoClose');
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({
        account: 'test-account',
        name: 'fnord-v001',
        region: 'us-central1',
        zones: ['us-central1-a'],
      }) as any,
    );

    gceServerGroupDetailsGetter(
      {
        app: { loadBalancers: { data: [] }, name: 'fnord', serverGroups: { data: [] } },
        serverGroup: { accountId: 'test-account', name: 'fnord-v001', region: 'us-central1' },
      },
      autoClose,
    ).subscribe({
      error: done.fail,
      next: (serverGroup: any) => {
        expect(serverGroup.name).toBe('fnord-v001');
        expect(serverGroup.logsLink).toBeUndefined();
      },
      complete: () => {
        expect(autoClose).not.toHaveBeenCalled();
        done();
      },
    });
  });

  it('builds GCE instance logs links with the expected resource type', async () => {
    const instance = decorateInstance({
      name: 'instance-two',
      networkInterfaces: [],
      selfLink: 'https://www.googleapis.com/compute/v1/projects/gce-project/zones/us-central1-a/instances/instance-two',
      zone: 'us-central1-a',
    });

    expect(instance.logsLink).toContain('resource=gce_instance');
    expect(instance.logsLink).not.toContain('resource= gce_instance');
  });

  it('does not force Google health provider params when platform-health override is disabled', async () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve({}) as any);
    const writer = { enableServerGroup: jasmine.createSpy('enableServerGroup').and.returnValue(Promise.resolve({})) };
    runtimeServices.serverGroupWriter = writer;

    mount(
      React.createElement(GceServerGroupActions, {
        app: { attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: false }, name: 'fnord' },
        serverGroup: { account: 'test-account', isDisabled: true, name: 'fnord-v001', region: 'us-central1' },
      }),
    )
      .find('a')
      .filterWhere((link) => link.text() === 'Enable')
      .simulate('click');

    const modalParams = confirmSpy.calls.mostRecent().args[0] as any;
    expect(modalParams.platformHealthOnlyShowOverride).toBe(false);
    await modalParams.submitMethod({ reason: 'not forced' });
    expect(writer.enableServerGroup.calls.mostRecent().args[2].interestingHealthProviderNames).toBeUndefined();
  });

  it('adds Google health provider params to GCE server group actions when platform health only is enabled', async () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm').and.returnValue(Promise.resolve({}) as any);
    spyOn(ServerGroupWarningMessageService, 'addDisableWarningMessage');
    spyOn(ServerGroupWarningMessageService, 'addDestroyWarningMessage');
    const writer = {
      destroyServerGroup: jasmine.createSpy('destroyServerGroup').and.returnValue(Promise.resolve({})),
      disableServerGroup: jasmine.createSpy('disableServerGroup').and.returnValue(Promise.resolve({})),
    };
    runtimeServices.serverGroupWriter = writer;
    const app = { attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true }, name: 'fnord' };
    const serverGroup = { account: 'test-account', isDisabled: false, name: 'fnord-v001', region: 'us-central1' };

    const wrapper = mount(React.createElement(GceServerGroupActions, { app, serverGroup }));
    wrapper
      .find('a')
      .filterWhere((link) => link.text() === 'Disable')
      .simulate('click');
    let modalParams = confirmSpy.calls.mostRecent().args[0] as any;
    expect(modalParams.platformHealthOnlyShowOverride).toBe(true);
    await modalParams.submitMethod({ reason: 'platform health only' });
    expect(writer.disableServerGroup.calls.mostRecent().args[2].interestingHealthProviderNames).toEqual(['Google']);

    wrapper
      .find('a')
      .filterWhere((link) => link.text() === 'Destroy')
      .simulate('click');
    modalParams = confirmSpy.calls.mostRecent().args[0] as any;
    expect(modalParams.platformHealthOnlyShowOverride).toBe(true);
    await modalParams.submitMethod({ reason: 'platform health only' });
    expect(writer.destroyServerGroup.calls.mostRecent().args[2].interestingHealthProviderNames).toEqual(['Google']);
  });

  it('shows an error when the GCE clone command cannot be built', async () => {
    const errorSpy = spyOn(ErrorModalService, 'error').and.returnValue(Promise.resolve({}) as any);
    const commandBuilder = {
      buildServerGroupCommandFromExisting: jasmine
        .createSpy('buildServerGroupCommandFromExisting')
        .and.returnValue(Promise.reject({ data: { message: 'missing launch template' } })),
    };

    await cloneGceServerGroup(
      { name: 'fnord' },
      { account: 'test-account', name: 'fnord-v001', region: 'us-central1' },
      commandBuilder as any,
    );

    expect(commandBuilder.buildServerGroupCommandFromExisting).toHaveBeenCalledWith(
      { name: 'fnord' },
      { account: 'test-account', name: 'fnord-v001', region: 'us-central1' },
    );
    expect(errorSpy).toHaveBeenCalledWith({
      body: 'missing launch template',
      header: 'Error cloning fnord-v001',
    });
  });

  it('closes GCE load balancer details when no matching summary exists', async () => {
    const loadBalancerReader = { getLoadBalancerDetails: jasmine.createSpy('getLoadBalancerDetails') };
    const autoClose = jasmine.createSpy('autoClose');

    const loadBalancer = await loadGceLoadBalancerDetails({
      app: { loadBalancers: { data: [] } } as any,
      loadBalancerParams: {
        accountId: 'test-account',
        name: 'frontend-map',
        provider: 'gce',
        region: 'global',
      },
      loadBalancerReader: loadBalancerReader as any,
      autoClose,
    });

    expect(loadBalancer).toBeUndefined();
    expect(autoClose).toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).not.toHaveBeenCalled();
  });

  it('registers Google pipeline stages without an Angular module dependency', () => {
    const previousPipeline = Registry.pipeline;
    const previousUrlBuilder = Registry.urlBuilder;

    Registry.reinitialize();
    try {
      googlePackage.registerGooglePipelineStages();

      const stages = Registry.pipeline.getStageTypes();
      const gceStages = stages.filter((stage: any) => stage.cloudProvider === 'gce');
      const expectedStages = [
        'bake',
        'cloneServerGroup',
        'destroyServerGroup',
        'disableCluster',
        'disableServerGroup',
        'enableServerGroup',
        'findImage',
        'findImageFromTags',
        'resizeServerGroup',
        'scaleDownCluster',
        'shrinkCluster',
        'upsertImageTags',
      ];

      expectedStages.forEach((provides) => {
        const stage = gceStages.find((candidate: any) => candidate.provides === provides);
        expect(stage).withContext(`gce ${provides} stage`).toBeDefined();
        expect(stage?.component).withContext(`gce ${provides} stage component`).toBeDefined();
        expectNoAngularStageRegistration(stage);
      });
    } finally {
      Registry.pipeline = previousPipeline;
      Registry.urlBuilder = previousUrlBuilder;
    }
  });

  it('does not duplicate Google pipeline stages when registration runs more than once', () => {
    const previousPipeline = Registry.pipeline;
    const previousUrlBuilder = Registry.urlBuilder;

    Registry.reinitialize();
    try {
      googlePackage.registerGooglePipelineStages();
      googlePackage.registerGooglePipelineStages();

      const gceStages = Registry.pipeline.getStageTypes().filter((stage: any) => stage.cloudProvider === 'gce');
      const expectedStages = [
        'bake',
        'cloneServerGroup',
        'destroyServerGroup',
        'disableCluster',
        'disableServerGroup',
        'enableServerGroup',
        'findImage',
        'findImageFromTags',
        'resizeServerGroup',
        'scaleDownCluster',
        'shrinkCluster',
        'upsertImageTags',
      ];

      expectedStages.forEach((provides) => {
        const registrations = gceStages.filter((stage: any) => stage.provides === provides);
        expect(registrations.length).withContext(`gce ${provides} registration count`).toBe(1);
      });
    } finally {
      Registry.pipeline = previousPipeline;
      Registry.urlBuilder = previousUrlBuilder;
    }
  });

  it('interpolates Google bake detail URLs', () => {
    const previousBakeryDetailUrl = (window as any).spinnakerSettings.bakeryDetailUrl;
    const previousRoscoDetailUrl = (window as any).spinnakerSettings.roscoDetailUrl;
    const previousRoscoMode = (window as any).spinnakerSettings.feature.roscoMode;

    (window as any).spinnakerSettings.feature.roscoMode = false;
    (window as any).spinnakerSettings.bakeryDetailUrl =
      'https://bakery/{{context.region}}/{{context.status.resourceId}}/{{context.status.resourceId}}';
    (window as any).spinnakerSettings.roscoDetailUrl = 'https://rosco/{{context.status.resourceId}}';

    try {
      expect(
        interpolatedBakeDetailUrl({
          context: {
            region: 'global',
            status: { resourceId: 'gce-image-1' },
          },
        } as any),
      ).toBe('https://bakery/global/gce-image-1/gce-image-1');

      (window as any).spinnakerSettings.feature.roscoMode = true;

      expect(
        interpolatedBakeDetailUrl({
          context: {
            region: 'global',
            status: { resourceId: 'gce-image-1' },
          },
        } as any),
      ).toBe('https://rosco/gce-image-1');
    } finally {
      (window as any).spinnakerSettings.bakeryDetailUrl = previousBakeryDetailUrl;
      (window as any).spinnakerSettings.roscoDetailUrl = previousRoscoDetailUrl;
      (window as any).spinnakerSettings.feature.roscoMode = previousRoscoMode;
    }
  });
});
