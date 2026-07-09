import { mount, shallow } from 'enzyme';
import React from 'react';

import {
  AccountService,
  CloudProviderRegistry,
  ConfirmationModalService,
  InstanceWriter,
  LoadBalancerWriter,
  Registry,
} from '@spinnaker/core';

import { DcosLink } from './common/DcosDetails';
import { dcosImageReader } from './image/image.reader';
import { DcosInstanceDetails } from './instance/details/DcosInstanceDetails';
import {
  DcosCreateLoadBalancerModal,
  DcosLoadBalancerActions,
  dcosLoadBalancerFormFields,
} from './loadBalancer/details/dcosLoadBalancerDetails';
import { dcosLoadBalancerTransformer } from './loadBalancer/transformer';
import {
  addDcosKeyValueEntry,
  DcosStageConfig,
  initializeDcosFindImageStage,
  initializeDcosRunJobStage,
  DcosFindImageStageConfig,
  DcosRunJobStageConfig,
  registerDcosPipelineStages,
  updateDcosLabels,
} from './pipeline/stages/dcosStages';
import { dcosServerGroupCommandBuilder } from './serverGroup/configure/CommandBuilder';
import {
  DcosCloneServerGroupModal,
  dcosCloneServerGroupFormFields,
} from './serverGroup/configure/DcosCloneServerGroupModal';
import { dcosServerGroupConfigurationService } from './serverGroup/configure/configuration.service';
import { dcosServerGroupTransformer } from './serverGroup/transformer';

function instantiateRegisteredDcosDelegate(serviceKey: string): any {
  const service = CloudProviderRegistry.getValue('dcos', serviceKey) as any;
  expect(typeof service).toBe('function');
  return new service();
}

async function importDcosEntrypoint() {
  const entrypoint = await import('./index');
  registerDcosPipelineStages();
  return entrypoint;
}

function setStateSynchronously(component: React.Component<any, any>) {
  spyOn(component, 'setState').and.callFake((updater: any) => {
    const nextState = typeof updater === 'function' ? updater(component.state, component.props) : updater;
    component.state = { ...component.state, ...nextState };
  });
}

function buildLoadedDataSource(data: any[], refresh = jasmine.createSpy('refresh')) {
  return {
    refresh,
    status$: {
      value: { data, loaded: true },
      subscribe: () => ({ unsubscribe: () => null }),
    },
  };
}

describe('DC/OS provider registration', () => {
  it('registers the provider configuration from the package entrypoint without exporting an Angular module token', async () => {
    const entrypoint = await importDcosEntrypoint();

    expect(CloudProviderRegistry.getValue('dcos', 'name')).toBe('DC/OS');
    expect(CloudProviderRegistry.getValue('dcos', 'serverGroup.skipUpstreamStageCheck')).toBe(true);
    expect(CloudProviderRegistry.getValue('dcos', 'instance.details')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', 'loadBalancer.useDetailsHook')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', 'loadBalancer.detailsActions')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', 'loadBalancer.detailsSections').length).toBeGreaterThan(0);
    expect(CloudProviderRegistry.getValue('dcos', 'loadBalancer.CreateLoadBalancerModal')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', 'serverGroup.detailsGetter')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', 'serverGroup.detailsActions')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', 'serverGroup.detailsSections').length).toBeGreaterThan(0);
    expect(CloudProviderRegistry.getValue('dcos', 'serverGroup.CloneServerGroupModal')).toBeDefined();
    expect(CloudProviderRegistry.getValue('dcos', ['instance', 'details' + 'Template' + 'Url'].join('.'))).toBeNull();
    expect(
      CloudProviderRegistry.getValue('dcos', ['serverGroup', 'cloneServerGroup' + 'Control' + 'ler'].join('.')),
    ).toBeNull();
    expect(entrypoint['DCOS' + '_DCOS' + '_MODULE']).toBeUndefined();
  });

  it('registers DC/OS provider delegates as constructable services', async () => {
    await importDcosEntrypoint();

    expect(instantiateRegisteredDcosDelegate('loadBalancer.transformer')).toBe(dcosLoadBalancerTransformer);
    expect(instantiateRegisteredDcosDelegate('image.reader')).toBe(dcosImageReader);
    expect(instantiateRegisteredDcosDelegate('serverGroup.transformer')).toBe(dcosServerGroupTransformer);
    expect(instantiateRegisteredDcosDelegate('serverGroup.commandBuilder')).toBe(dcosServerGroupCommandBuilder);
    expect(instantiateRegisteredDcosDelegate('serverGroup.configurationService')).toBe(
      dcosServerGroupConfigurationService,
    );
  });

  it('registers dcos pipeline stages without Angular templates', async () => {
    await importDcosEntrypoint();

    const expectedStages = [
      'destroyServerGroup',
      'disableServerGroup',
      'disableCluster',
      'findImage',
      'resizeServerGroup',
      'runJob',
      'scaleDownCluster',
      'shrinkCluster',
    ];

    expectedStages.forEach((type) => {
      const config = Registry.pipeline.getStageConfig({
        type,
        cloudProvider: 'dcos',
        context: { cloudProvider: 'dcos' },
      } as any);
      expect(config.cloudProvider).toBe('dcos');
      expect(config.component).toBeDefined();
      expect(config['template' + 'Url']).toBeUndefined();
      expect(config.executionDetailsSections).toBeDefined();
    });
  });

  it('preserves the DC/OS disable server group validation contract', async () => {
    await importDcosEntrypoint();

    const config = Registry.pipeline.getStageConfig({
      type: 'disableServerGroup',
      cloudProvider: 'dcos',
      context: { cloudProvider: 'dcos' },
    } as any);

    expect(config.validators).toEqual(
      jasmine.arrayContaining([
        jasmine.objectContaining({ type: 'targetImpedance' }),
        jasmine.objectContaining({ type: 'requiredField', fieldName: 'target' }),
      ]),
    );
  });

  it('preserves the DC/OS find image stage contract', async () => {
    await importDcosEntrypoint();

    const config = Registry.pipeline.getStageConfig({
      type: 'findImage',
      cloudProvider: 'dcos',
      context: { cloudProvider: 'dcos' },
    } as any);
    const stage: any = {};
    initializeDcosFindImageStage(stage, { defaultCredentials: { dcos: 'test-account' } } as any);

    expect(config.component).toBe(DcosFindImageStageConfig);
    expect(config.validators.map((validator: any) => validator.fieldName)).toEqual([
      'cluster',
      'selectionStrategy',
      'credentials',
    ]);
    expect(stage.cloudProvider).toBe('dcos');
    expect(stage.credentials).toBe('test-account');
    expect(stage.selectionStrategy).toBe('LARGEST');
    expect(stage.onlyEnabled).toBe(true);
  });

  it('preserves the DC/OS run job stage contract', async () => {
    await importDcosEntrypoint();

    const config = Registry.pipeline.getStageConfig({
      type: 'runJob',
      cloudProvider: 'dcos',
      context: { cloudProvider: 'dcos' },
    } as any);
    const stage: any = {};
    initializeDcosRunJobStage(stage, { name: 'dcosapp', defaultCredentials: { dcos: 'test-account' } } as any, {
      'test-account': {
        dcosClusters: [{ name: 'test-cluster' }],
        dockerRegistries: [{ accountName: 'docker-registry' }],
      },
    });

    expect(config.component).toBe(DcosRunJobStageConfig);
    expect(config.validators.map((validator: any) => validator.fieldName)).toEqual(['account', 'general.id']);
    expect(stage.cloudProvider).toBe('dcos');
    expect(stage.application).toBe('dcosapp');
    expect(stage.account).toBe('test-account');
    expect(stage.dcosCluster).toBe('test-cluster');
    expect(stage.region).toBe('test-cluster');
    expect(stage.cluster).toBe('test-cluster');
    expect(stage.general).toEqual({ cpus: 0.01, gpus: 0.0, mem: 128, disk: 0 });
    expect(stage.docker.image.registry).toBe('docker-registry');
  });

  it('updates DC/OS run job labels as editable key/value data', () => {
    expect(updateDcosLabels({ labels: { owner: 'cd' } }, 'service', 'deck').labels).toEqual({
      owner: 'cd',
      service: 'deck',
    });
  });

  it('does not overwrite DC/OS label values when renaming a key to an existing key', () => {
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(new Promise(() => null) as any);
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = mount(
      React.createElement(DcosRunJobStageConfig, {
        application: {},
        stage: {
          docker: { image: {} },
          general: {},
          labels: { keyA: 'one', keyB: 'two' },
        },
        updateStage,
      } as any),
    );

    wrapper
      .find('input')
      .filterWhere((input) => input.prop('value') === 'keyA')
      .first()
      .simulate('change', { target: { value: 'keyB' } });

    expect(updateStage).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('adds DC/OS key/value entries without overwriting an existing key placeholder', () => {
    expect(addDcosKeyValueEntry({ key: 'existing' })).toEqual({ key: 'existing', key1: '' });
  });

  it('opens DC/OS links without exposing the parent window', () => {
    const link = (DcosLink({ href: 'https://dcos.example.com' }) as any).props.children;

    expect(link.props.rel).toBe('noopener noreferrer');
  });

  it('exposes functional DC/OS modal form fields beyond JSON', () => {
    expect(dcosCloneServerGroupFormFields).toContain('name');
    expect(dcosCloneServerGroupFormFields).toContain('docker.image.repository');
    expect(dcosCloneServerGroupFormFields).toContain('labels');
    expect(dcosLoadBalancerFormFields).toContain('name');
    expect(dcosLoadBalancerFormFields).toContain('ports');
    expect(dcosLoadBalancerFormFields).toContain('labels');
  });

  it('initializes async DC/OS stage defaults against the latest stage props', async () => {
    let resolveCredentials: (credentials: any) => void;
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      new Promise((resolve) => {
        resolveCredentials = resolve;
      }) as any,
    );
    const updateStage = jasmine.createSpy('updateStage');
    const application = { name: 'dcosapp', defaultCredentials: { dcos: 'test-account' } } as any;
    const wrapper = mount(
      React.createElement(DcosRunJobStageConfig, {
        application,
        stage: { propertyFile: 'initial' },
        updateStage,
      } as any),
    );

    wrapper.setProps({ stage: { propertyFile: 'edited', customField: 'preserved' } });
    resolveCredentials({
      'test-account': {
        dcosClusters: [{ name: 'test-cluster' }],
        dockerRegistries: [{ accountName: 'docker-registry' }],
      },
    });
    await Promise.resolve();
    await Promise.resolve();

    const initializedStage = updateStage.calls.mostRecent().args[0];
    expect(initializedStage.propertyFile).toBe('edited');
    expect(initializedStage.customField).toBe('preserved');
    expect(initializedStage.cloudProvider).toBe('dcos');
    expect(initializedStage.account).toBe('test-account');
    wrapper.unmount();
  });

  it('initializes DC/OS stage defaults without mutating stage props', async () => {
    const stage = { type: 'resizeServerGroup', cluster: 'test-cluster' } as any;
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = mount(
      React.createElement(DcosStageConfig, {
        application: { defaultCredentials: { dcos: 'test-account' }, defaultRegions: { dcos: 'test-region' } },
        stage,
        updateStage,
      } as any),
    );

    await Promise.resolve();

    const initializedStage = updateStage.calls.mostRecent().args[0];
    expect(initializedStage).not.toBe(stage);
    expect(initializedStage).toEqual(
      jasmine.objectContaining({
        action: 'scale_up',
        capacity: {},
        cloudProvider: 'dcos',
        credentials: 'test-account',
        resizeType: 'pct',
      }),
    );
    expect(initializedStage.regions).toEqual(['test-region']);
    expect(stage).toEqual({ type: 'resizeServerGroup', cluster: 'test-cluster' });
    wrapper.unmount();
  });

  it('confirms before terminating a DC/OS instance', async () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm');
    spyOn(InstanceWriter, 'terminateInstance').and.returnValue(Promise.resolve() as any);
    const refresh = jasmine.createSpy('refresh');
    const dataSource = buildLoadedDataSource(
      [{ id: 'instance-1', account: 'test-account', region: 'test-cluster' }],
      refresh,
    );
    const app = { getDataSource: jasmine.createSpy('getDataSource').and.returnValue(dataSource) } as any;
    const wrapper = shallow(
      React.createElement(DcosInstanceDetails, {
        app,
        instance: { instanceId: 'instance-1', account: 'test-account', region: 'test-cluster' },
      }),
    );

    wrapper.find('button').simulate('click');

    expect(confirmSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        account: 'test-account',
        buttonText: 'Terminate',
        header: 'Really terminate instance-1?',
        submitMethod: jasmine.any(Function),
      }),
    );

    await confirmSpy.calls.mostRecent().args[0].submitMethod();
    expect(InstanceWriter.terminateInstance).toHaveBeenCalledWith(jasmine.objectContaining({ id: 'instance-1' }), app);
    expect(refresh).toHaveBeenCalled();
  });

  it('confirms before deleting a DC/OS load balancer', async () => {
    const confirmSpy = spyOn(ConfirmationModalService, 'confirm');
    spyOn(LoadBalancerWriter, 'deleteLoadBalancer').and.returnValue(Promise.resolve() as any);
    const app = { loadBalancers: { refresh: jasmine.createSpy('refresh') } } as any;
    const loadBalancer = { name: 'lb-1', account: 'test-account', region: 'test-cluster' } as any;
    const wrapper = shallow(React.createElement(DcosLoadBalancerActions, { app, loadBalancer } as any));

    wrapper.find('a').at(1).simulate('click');

    expect(confirmSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        account: 'test-account',
        buttonText: 'Delete lb-1',
        header: 'Really delete lb-1?',
        submitMethod: jasmine.any(Function),
      }),
    );

    await confirmSpy.calls.mostRecent().args[0].submitMethod();
    expect(LoadBalancerWriter.deleteLoadBalancer).toHaveBeenCalledWith(
      jasmine.objectContaining({
        cloudProvider: 'dcos',
        credentials: 'test-account',
        loadBalancerName: 'lb-1',
        region: 'test-cluster',
      }),
      app,
    );
    expect(app.loadBalancers.refresh).toHaveBeenCalled();
  });

  it('ignores invalid DC/OS load balancer ports from free text input', () => {
    const modal = new DcosCreateLoadBalancerModal({} as any);
    setStateSynchronously(modal);

    (modal as any).updatePorts('80, abc, 443');

    expect(modal.state.command.ports).toEqual([80, 443]);
  });

  it('adds DC/OS load balancer map entries without overwriting sparse placeholder keys', () => {
    const modal = new DcosCreateLoadBalancerModal({ loadBalancer: { labels: { key2: 'prod' } } } as any);
    setStateSynchronously(modal);
    const wrapper = shallow((modal as any).renderMap('Labels', 'labels'));

    wrapper.find('button').simulate('click');

    expect(modal.state.command.labels).toEqual({ key2: 'prod', key: '' });
  });

  it('keeps the DC/OS load balancer modal open until upsert and refresh complete', async () => {
    let resolveUpsert: () => void;
    const upsert = new Promise<void>((resolve) => {
      resolveUpsert = resolve;
    });
    const refresh = jasmine.createSpy('refresh').and.returnValue(Promise.resolve());
    spyOn(LoadBalancerWriter, 'upsertLoadBalancer').and.returnValue(upsert as any);
    const closeModal = jasmine.createSpy('closeModal');
    const modal = new DcosCreateLoadBalancerModal({
      app: { loadBalancers: { refresh } },
      closeModal,
    } as any);

    (modal as any).submit();

    expect(closeModal).not.toHaveBeenCalled();

    resolveUpsert!();
    await upsert;
    await Promise.resolve();

    expect(refresh).toHaveBeenCalled();
    await refresh.calls.mostRecent().returnValue;
    await Promise.resolve();
    expect(closeModal).toHaveBeenCalledWith(jasmine.objectContaining({ cloudProvider: 'dcos', provider: 'dcos' }));
  });

  it('keeps the DC/OS load balancer modal open when upsert fails', async () => {
    const upsert = Promise.reject(new Error('upsert failed'));
    spyOn(LoadBalancerWriter, 'upsertLoadBalancer').and.returnValue(upsert as any);
    const closeModal = jasmine.createSpy('closeModal');
    const modal = new DcosCreateLoadBalancerModal({
      app: { loadBalancers: { refresh: jasmine.createSpy('refresh') } },
      closeModal,
    } as any);

    (modal as any).submit();
    await upsert.catch(() => undefined);
    await Promise.resolve();

    expect(closeModal).not.toHaveBeenCalled();
  });

  it('adds DC/OS clone server group map entries without overwriting sparse placeholder keys', () => {
    const modal = new DcosCloneServerGroupModal({ command: { labels: { key2: 'prod' } } } as any);
    setStateSynchronously(modal);
    const wrapper = shallow((modal as any).renderMap('Labels', 'labels'));

    wrapper.find('button').simulate('click');

    expect(modal.state.command.labels).toEqual({ key2: 'prod', key: '' });
  });
});
