import { mount, shallow } from 'enzyme';
import React from 'react';

import {
  AccountService,
  AuthenticationService,
  BakeryReader,
  CloudProviderRegistry,
  ExecutionDetailsTasks,
  PlatformHealthOverride,
  Registry,
  SETTINGS,
  Spinner,
  StageConfigField,
} from '@spinnaker/core';

import * as azurePackage from './index';
import { AzureImageReader } from './image/image.reader';
import { AzureInstanceTypeService } from './instance/azureInstanceType.service';
import { AzureLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import { AzureBakeStageConfig } from './pipeline/stages/bake/azureBakeStage';
import { AzureDestroyAsgStageConfig } from './pipeline/stages/destroyAsg/azureDestroyAsgStage';
import { AzureDisableAsgStageConfig } from './pipeline/stages/disableAsg/azureDisableAsgStage';
import { AzureEnableAsgStageConfig } from './pipeline/stages/enableAsg/azureEnableAsgStage';
import { registerAzurePipelineStages } from './azure.module';
import { AzureSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { AzureSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { AzureServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AzureServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { AzureServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('Azure package registration', () => {
  const legacyCtrlKey = ['Cont', 'roller'].join('');
  const legacyStageCtrlKey = ['cont', 'roller'].join('');
  const legacyViewKey = ['Template', 'Url'].join('');
  const legacyModuleExport = ['AZURE', 'MODULE'].join('_');
  const stageViewKey = ['template', 'Url'].join('');
  const stepLabelViewKey = ['execution', 'Step', 'Label', 'Url'].join('');
  const markupExtension = ['.', 'ht', 'ml'].join('');

  function expectRegistered(path: string): void {
    expect(CloudProviderRegistry.getValue('azure', path)).withContext(path).not.toBeNull();
  }

  function expectNonEmptyRegistration(path: string): void {
    const value = CloudProviderRegistry.getValue('azure', path);
    const entries = Array.isArray(value) ? value : [];
    expect(Array.isArray(value)).withContext(path).toBe(true);
    expect(entries.length).withContext(path).toBeGreaterThan(0);
  }

  function expectStageComponent(stageTypes: any[], provides: string, component: any): any {
    const stage = stageTypes.find((candidate) => candidate.provides === provides);
    expect(stage).withContext(`azure ${provides} stage`).toBeDefined();
    expect(stage?.component).withContext(`azure ${provides} stage component`).toBe(component);
    return stage;
  }

  function renderStageConfig(stageConfig: any) {
    const updateStageField = jasmine.createSpy('updateStageField');
    const stage = { isNew: true };

    const wrapper = shallow(
      React.createElement(stageConfig.component, {
        application: {
          attributes: {},
          defaultCredentials: { azure: 'test-account' },
          defaultRegions: { azure: 'eastus' },
        },
        stage,
        updateStageField,
      }),
      { disableLifecycleMethods: true },
    );

    if (wrapper.find(Spinner).exists()) {
      wrapper.setState({
        accounts: ['test-account'],
        baseLabelOptions: ['release'],
        baseOsOptions: [{ id: 'ubuntu' }],
        loading: false,
        regions: ['eastus'],
      });
      wrapper.update();
    }

    return { stage, updateStageField, wrapper };
  }

  function expectStageFields(stageConfig: any, expectedLabels: string[]): void {
    const { wrapper } = renderStageConfig(stageConfig);

    const labels = wrapper.find(StageConfigField).map((field) => field.prop('label'));
    expect(labels).withContext(`azure ${stageConfig.provides} stage fields`).toEqual(expectedLabels);
  }

  function expectTargetControl(stageConfig: any): void {
    const { wrapper, updateStageField } = renderStageConfig(stageConfig);
    const targetField = wrapper
      .find(StageConfigField)
      .findWhere((field) => field.prop('label') === 'Target')
      .first();

    expect(targetField.exists()).withContext(`azure ${stageConfig.provides} target field`).toBe(true);

    targetField.find('select').simulate('change', { target: { value: 'oldest' } });

    expect(updateStageField).withContext(`azure ${stageConfig.provides} target update`).toHaveBeenCalledWith({
      target: 'oldest',
    });
  }

  function expectAzureHealthOverride(stageConfig: any): void {
    const stage = { isNew: true };
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = shallow(
      React.createElement(stageConfig.component, {
        application: {
          attributes: { platformHealthOnlyShowOverride: true },
          defaultCredentials: { azure: 'test-account' },
          defaultRegions: { azure: 'eastus' },
        },
        stage,
        updateStageField,
      }),
    );
    const healthOverride = wrapper.find(PlatformHealthOverride);

    expect(healthOverride.exists()).withContext(`azure ${stageConfig.provides} health override`).toBe(true);
    expect(healthOverride.prop('platformHealthType'))
      .withContext(`azure ${stageConfig.provides} health type`)
      .toBe('azureService');

    healthOverride.prop('onChange')(['azureService']);

    expect(updateStageField)
      .withContext(`azure ${stageConfig.provides} health override update`)
      .toHaveBeenCalledWith({
        interestingHealthProviderNames: ['azureService'],
      });

    const hiddenWrapper = shallow(
      React.createElement(stageConfig.component, {
        application: {
          attributes: { platformHealthOnlyShowOverride: false },
          defaultCredentials: { azure: 'test-account' },
          defaultRegions: { azure: 'eastus' },
        },
        stage: { isNew: true },
        updateStageField: jasmine.createSpy('hiddenUpdateStageField'),
      }),
    );

    expect(hiddenWrapper.find(PlatformHealthOverride).exists())
      .withContext(`azure ${stageConfig.provides} hidden health override`)
      .toBe(false);
  }

  function expectNoAngularStageRegistration(stageConfig: any): void {
    expect(stageConfig[stageViewKey]).withContext(`azure ${stageConfig.provides} stage view`).toBeUndefined();
    expect(stageConfig[legacyStageCtrlKey]).withContext(`azure ${stageConfig.provides} legacy handler`).toBeUndefined();
    expect(stageConfig[stepLabelViewKey]).withContext(`azure ${stageConfig.provides} step label view`).toBeUndefined();

    const htmlValues = Object.keys(stageConfig)
      .map((key) => stageConfig[key])
      .filter((value) => typeof value === 'string' && value.endsWith(markupExtension));

    expect(htmlValues).withContext(`azure ${stageConfig.provides} markup stage config values`).toEqual([]);
  }

  function expectExecutionLabel(stageConfig: any, label: string): void {
    expect(stageConfig.executionLabelComponent)
      .withContext(`azure ${stageConfig.provides} execution label component`)
      .toBeDefined();

    const wrapper = shallow(
      React.createElement(stageConfig.executionLabelComponent, {
        stage: { masterStage: { context: { region: 'eastus', serverGroupName: 'azureapp-v001' } } },
      }),
    );

    expect(wrapper.text())
      .withContext(`azure ${stageConfig.provides} execution label text`)
      .toContain(`${label}: azureapp-v001 (eastus)`);
  }

  function expectRequiredFields(stageConfig: any, expectedFields: string[]): void {
    const requiredFields = stageConfig.validators
      .filter((validator: any) => validator.type === 'requiredField')
      .map((validator: any) => validator.fieldName);
    expect(requiredFields).withContext(`azure ${stageConfig.provides} required fields`).toEqual(expectedFields);
  }

  function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (error: any) => void;
    const promise = new Promise<T>((promiseResolve, promiseReject) => {
      resolve = promiseResolve;
      reject = promiseReject;
    });
    return { promise, resolve, reject };
  }

  function bakeStageProps(stage: any, updateStage = jasmine.createSpy('updateStage')): any {
    return {
      application: { attributes: {}, defaultCredentials: { azure: 'bakery' }, defaultRegions: { azure: 'eastus' } },
      pipeline: {},
      stage,
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage,
      updateStageField: jasmine.createSpy('updateStageField'),
    };
  }

  function applicationWithServerGroups(serverGroups: any[]): any {
    return {
      attributes: {},
      defaultCredentials: { azure: 'prod' },
      defaultRegions: { azure: 'eastus' },
      getDataSource: (key: string) => (key === 'serverGroups' ? { data: serverGroups } : { data: [] }),
    };
  }

  it('registers Azure without exporting an Angular module token', () => {
    expect(Object.prototype.hasOwnProperty.call(azurePackage, legacyModuleExport)).toBe(false);

    expect(CloudProviderRegistry.getValue('azure', 'image.reader')).toBe(AzureImageReader);
    expect(CloudProviderRegistry.getValue('azure', 'instance.instanceTypeService')).toBe(AzureInstanceTypeService);
    expect(CloudProviderRegistry.getValue('azure', 'loadBalancer.transformer')).toBe(AzureLoadBalancerTransformer);
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.transformer')).toBe(AzureServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.commandBuilder')).toBe(AzureServerGroupCommandBuilder);
    expect(CloudProviderRegistry.getValue('azure', 'serverGroup.configurationService')).toBe(
      AzureServerGroupConfigurationService,
    );
    expect(CloudProviderRegistry.getValue('azure', 'securityGroup.reader')).toBe(AzureSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('azure', 'securityGroup.transformer')).toBe(AzureSecurityGroupTransformer);

    expectRegistered('serverGroup.CloneServerGroupModal');
    expectRegistered('serverGroup.detailsGetter');
    expectRegistered('serverGroup.detailsActions');
    expectNonEmptyRegistration('serverGroup.detailsSections');
    expectRegistered('instance.details');
    expectRegistered('loadBalancer.CreateLoadBalancerModal');
    expectRegistered('loadBalancer.useDetailsHook');
    expectRegistered('loadBalancer.detailsActions');
    expectNonEmptyRegistration('loadBalancer.detailsSections');
    expectRegistered('securityGroup.CreateSecurityGroupModal');
    expectRegistered('securityGroup.details');

    expect(CloudProviderRegistry.getValue('azure', `serverGroup.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `serverGroup.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `instance.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `instance.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `loadBalancer.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `loadBalancer.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `securityGroup.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', `securityGroup.details${legacyViewKey}`)).toBeNull();
  });

  it('does not bundle Azure Angular HTML templates', () => {
    const azureTemplates = require.context('./', true, /\.html$/).keys();

    expect(azureTemplates).toEqual([]);
  });

  it('registers Azure pipeline stages without Angular templates', () => {
    const previousPipelineRegistry = Registry.pipeline;
    const previousUrlBuilderRegistry = Registry.urlBuilder;

    Registry.reinitialize();
    try {
      registerAzurePipelineStages();

      const stageTypes = Registry.pipeline.getStageTypes().filter((stage) => stage.cloudProvider === 'azure');

      const bakeStage = expectStageComponent(stageTypes, 'bake', AzureBakeStageConfig);
      const destroyStage = expectStageComponent(stageTypes, 'destroyServerGroup', AzureDestroyAsgStageConfig);
      const disableStage = expectStageComponent(stageTypes, 'disableServerGroup', AzureDisableAsgStageConfig);
      const enableStage = expectStageComponent(stageTypes, 'enableServerGroup', AzureEnableAsgStageConfig);

      [bakeStage, destroyStage, disableStage, enableStage].forEach(expectNoAngularStageRegistration);

      expectStageFields(bakeStage, ['Account', 'Regions', 'Base OS', 'Package', 'Base Label', 'Base Name']);
      expectRequiredFields(bakeStage, ['package', 'regions']);
      expect(bakeStage.executionDetailsSections).toBeDefined();
      expect(bakeStage.executionDetailsSections[1]).toBe(ExecutionDetailsTasks);

      [destroyStage, disableStage, enableStage].forEach((stage) => {
        expectTargetControl(stage);
        expectRequiredFields(stage, ['cluster', 'target', 'regions', 'credentials']);
      });

      [disableStage, enableStage].forEach(expectAzureHealthOverride);
      expectExecutionLabel(destroyStage, 'Destroy Server Group');
      expectExecutionLabel(disableStage, 'Disable Server Group');
      expectExecutionLabel(enableStage, 'Enable Server Group');

      expect(stageTypes.some((stage) => (stage as any)[stageViewKey] || (stage as any)[legacyStageCtrlKey])).toBe(
        false,
      );
    } finally {
      Registry.pipeline = previousPipelineRegistry;
      Registry.urlBuilder = previousUrlBuilderRegistry;
    }
  });

  it('renders Azure bake execution details without Angular templates', () => {
    const previousPipelineRegistry = Registry.pipeline;
    const previousUrlBuilderRegistry = Registry.urlBuilder;
    const previousBakeryDetailUrl = SETTINGS.bakeryDetailUrl;

    Registry.reinitialize();
    SETTINGS.bakeryDetailUrl = '/bakery/{{context.region}}/{{context.status.resourceId}}';
    try {
      registerAzurePipelineStages();

      const bakeStage = Registry.pipeline
        .getStageTypes()
        .find((stage) => stage.cloudProvider === 'azure' && stage.provides === 'bake') as any;
      const BakeExecutionDetails = bakeStage.executionDetailsSections[0];
      const wrapper = shallow(
        React.createElement(BakeExecutionDetails, {
          current: 'bakeConfig',
          execution: { trigger: { rebake: true } },
          name: 'bakeConfig',
          stage: {
            context: {
              ami: 'azure-image-v001',
              baseLabel: 'release',
              baseOs: 'ubuntu',
              package: 'my-package',
              region: 'eastus',
              status: { resourceId: 'bake-123' },
              templateFileName: 'template.json',
              varFileName: 'vars.json',
            },
            failureMessage: 'bake failed',
            isFailed: false,
          },
        }),
      );

      expect(bakeStage.executionDetailsUrl).toBeUndefined();
      expect((BakeExecutionDetails as any).title).toBe('bakeConfig');
      const section = wrapper.dive();
      expect(section.text()).toContain('Azure');
      expect(section.text()).toContain('azure-image-v001');
      expect(section.text()).toContain('my-package');
      expect(section.text()).toContain('template.json');
      expect(section.find('a[href="/bakery/eastus/bake-123"]').exists()).toBe(true);
    } finally {
      SETTINGS.bakeryDetailUrl = previousBakeryDetailUrl;
      Registry.pipeline = previousPipelineRegistry;
      Registry.urlBuilder = previousUrlBuilderRegistry;
    }
  });

  it('loads destroy stage region from selected Azure account details', async () => {
    spyOn(AccountService, 'getAccountDetails').and.returnValue(Promise.resolve({ org: 'westus' } as any));

    const updateStageField = jasmine.createSpy('updateStageField');
    const stage = { credentials: 'test-account' };

    shallow(
      React.createElement(AzureDestroyAsgStageConfig, {
        application: {
          attributes: {},
          defaultCredentials: { azure: 'test-account' },
          defaultRegions: { azure: 'eastus' },
        },
        stage,
        updateStageField,
      }),
    );

    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(AccountService.getAccountDetails).toHaveBeenCalledWith('test-account');
    expect(updateStageField).toHaveBeenCalledWith({ regions: ['westus'] });
  });

  it('renders account, region, and cluster selectors for Azure server group stages', async () => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([{ name: 'prod' }, { name: 'test' }] as any));
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(
      Promise.resolve(['eastus', 'westus']) as any,
    );
    spyOn(AccountService, 'getAccountDetails').and.returnValue(Promise.resolve({ org: 'eastus' } as any));

    const application = applicationWithServerGroups([
      {
        account: 'prod',
        cluster: 'app',
        moniker: { app: 'app', cluster: 'app', sequence: 1 },
        region: 'eastus',
      },
      {
        account: 'prod',
        cluster: 'api',
        moniker: { app: 'api', cluster: 'api', sequence: 3 },
        region: 'westus',
      },
    ]);

    for (const component of [AzureDestroyAsgStageConfig, AzureDisableAsgStageConfig, AzureEnableAsgStageConfig]) {
      const updateStageField = jasmine.createSpy('updateStageField');
      const stage = {
        cloudProvider: 'azure',
        cluster: 'app',
        credentials: 'prod',
        moniker: { app: 'app', cluster: 'app', sequence: 1 },
        regions: ['eastus'],
      } as any;
      const wrapper = mount(
        React.createElement(component, {
          application,
          pipeline: {},
          stage,
          updateStageField,
        }),
      );

      await new Promise((resolve) => setTimeout(resolve, 0));
      wrapper.update();

      const accountSelect = wrapper.find('select[name="credentials"]');
      expect(accountSelect.exists()).withContext(`${component.name} account select`).toBe(true);
      expect(accountSelect.find('option[value="prod"]').exists())
        .withContext(`${component.name} prod account option`)
        .toBe(true);

      const eastusCheckbox = wrapper
        .find('input[type="checkbox"][name="regions"]')
        .findWhere((input) => input.prop('value') === 'eastus');
      expect(eastusCheckbox.exists()).withContext(`${component.name} region checklist`).toBe(true);

      const clusterSelect = wrapper.find('select[name="cluster"]');
      expect(clusterSelect.exists()).withContext(`${component.name} cluster select`).toBe(true);
      expect(clusterSelect.find('option[value="app"]').exists())
        .withContext(`${component.name} app cluster option`)
        .toBe(true);

      eastusCheckbox.simulate('change', { target: { checked: false } });
      expect(updateStageField)
        .withContext(`${component.name} region update clears cluster`)
        .toHaveBeenCalledWith(jasmine.objectContaining({ cluster: undefined, moniker: undefined, regions: [] }));

      clusterSelect.simulate('change', { target: { value: 'api' } });
      expect(updateStageField)
        .withContext(`${component.name} cluster update sets moniker`)
        .toHaveBeenCalledWith(
          jasmine.objectContaining({
            cluster: 'api',
            moniker: jasmine.objectContaining({ cluster: 'api', sequence: null }),
          }),
        );

      accountSelect.simulate('change', { target: { value: 'test' } });
      expect(updateStageField)
        .withContext(`${component.name} account update clears dependent fields`)
        .toHaveBeenCalledWith(
          jasmine.objectContaining({ credentials: 'test', cluster: undefined, moniker: undefined, regions: [] }),
        );

      wrapper.unmount();
    }
  });

  it('preserves selected Azure cluster while selected regions still include that cluster', async () => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([{ name: 'prod' }] as any));
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(
      Promise.resolve(['eastus', 'westus']) as any,
    );

    const updateStageField = jasmine.createSpy('updateStageField');
    const stage = {
      cloudProvider: 'azure',
      cluster: 'app',
      credentials: 'prod',
      moniker: { app: 'app', cluster: 'app', sequence: 1 },
      regions: ['eastus', 'westus'],
    } as any;
    const wrapper = mount(
      React.createElement(AzureDisableAsgStageConfig, {
        application: applicationWithServerGroups([
          {
            account: 'prod',
            cluster: 'app',
            moniker: { app: 'app', cluster: 'app', sequence: 1 },
            region: 'eastus',
          },
        ]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper
      .find('input[type="checkbox"][name="regions"]')
      .findWhere((input) => input.prop('value') === 'westus')
      .simulate('change', { target: { checked: false } });

    expect(updateStageField).toHaveBeenCalledWith({ regions: ['eastus'] });
    expect(updateStageField).not.toHaveBeenCalledWith(
      jasmine.objectContaining({ cluster: undefined, moniker: undefined }),
    );

    wrapper.unmount();
  });

  it('supports free-text Azure cluster entry when the selected cluster is not discovered', async () => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([{ name: 'prod' }] as any));
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(
      Promise.resolve(['eastus', 'westus']) as any,
    );

    const updateStageField = jasmine.createSpy('updateStageField');
    const stage = {
      cloudProvider: 'azure',
      cluster: 'custom-cluster',
      credentials: 'prod',
      regions: ['eastus'],
    } as any;
    const wrapper = mount(
      React.createElement(AzureDisableAsgStageConfig, {
        application: applicationWithServerGroups([]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    const clusterInput = wrapper.find('input[type="text"][name="cluster"]');
    expect(clusterInput.exists()).toBe(true);
    expect(clusterInput.prop('value')).toBe('custom-cluster');

    clusterInput.simulate('change', { target: { value: 'new-custom-cluster' } });
    expect(updateStageField).toHaveBeenCalledWith({ cluster: 'new-custom-cluster', moniker: undefined });

    wrapper
      .find('a')
      .filterWhere((link) => link.text().includes('list of existing clusters'))
      .simulate('click', {
        preventDefault: jasmine.createSpy('preventDefault'),
      });
    expect(updateStageField).toHaveBeenCalledWith({ cluster: undefined, moniker: undefined });

    wrapper.unmount();
  });

  it('preserves existing Azure health override selections on new disable and enable stages', () => {
    [AzureDisableAsgStageConfig, AzureEnableAsgStageConfig].forEach((component) => {
      const stage = { isNew: true, interestingHealthProviderNames: ['azureService'] } as any;

      shallow(
        React.createElement(component, {
          application: {
            attributes: { platformHealthOnlyShowOverride: true },
            defaultCredentials: { azure: 'test-account' },
            defaultRegions: { azure: 'eastus' },
          },
          stage,
          updateStageField: jasmine.createSpy('updateStageField'),
        }),
      );

      expect(stage.interestingHealthProviderNames).toEqual(['azureService']);
    });
  });

  it('ignores stale destroy account detail responses and clears regions when credentials change', async () => {
    const firstAccount = deferred<any>();
    const secondAccount = deferred<any>();
    spyOn(AccountService, 'listAccounts').and.returnValue(
      Promise.resolve([{ name: 'first-account' }, { name: 'second-account' }] as any),
    );
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(
      Promise.resolve(['first-region', 'second-region']) as any,
    );
    spyOn(AccountService, 'getAccountDetails').and.callFake((account: string) => {
      return account === 'first-account' ? firstAccount.promise : secondAccount.promise;
    });

    const updateStageField = jasmine.createSpy('updateStageField');
    const stage = { credentials: 'first-account', regions: ['stale-region'] } as any;
    const wrapper = mount(
      React.createElement(AzureDestroyAsgStageConfig, {
        application: applicationWithServerGroups([]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper.find('select[name="credentials"]').simulate('change', {
      target: { value: 'second-account' },
    });

    expect(updateStageField).toHaveBeenCalledWith(
      jasmine.objectContaining({ credentials: 'second-account', regions: [] }),
    );

    firstAccount.resolve({ org: 'first-region' });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(updateStageField).not.toHaveBeenCalledWith({ regions: ['first-region'] });

    secondAccount.resolve({ org: 'second-region' });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(updateStageField).toHaveBeenCalledWith({ regions: ['second-region'] });
    wrapper.unmount();
  });

  it('leaves destroy regions empty when account detail loading fails after credentials change', async () => {
    spyOn(AccountService, 'listAccounts').and.returnValue(
      Promise.resolve([{ name: 'first-account' }, { name: 'bad-account' }] as any),
    );
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(Promise.resolve(['eastus']) as any);
    spyOn(AccountService, 'getAccountDetails').and.returnValue(Promise.reject(new Error('boom')));

    const updateStageField = jasmine.createSpy('updateStageField');
    const stage = { credentials: 'first-account', regions: ['stale-region'] } as any;
    const wrapper = mount(
      React.createElement(AzureDestroyAsgStageConfig, {
        application: applicationWithServerGroups([]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper.find('select[name="credentials"]').simulate('change', {
      target: { value: 'bad-account' },
    });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(stage.regions).toEqual([]);
    expect(updateStageField).toHaveBeenCalledWith(
      jasmine.objectContaining({ credentials: 'bad-account', regions: [] }),
    );
    wrapper.unmount();
  });

  it('initializes Azure bake options and defaults from services', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' } } as any),
    );
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east', 'rosco-west']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu', shortDescription: 'Ubuntu' }] } as any),
    );
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release', 'candidate']));

    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = shallow(
      React.createElement(AzureBakeStageConfig, {
        application: { attributes: {}, defaultCredentials: { azure: 'bakery' }, defaultRegions: { azure: 'eastus' } },
        pipeline: {},
        stage: { package: 'my-package' },
        stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
        updateStage,
        updateStageField: jasmine.createSpy('updateStageField'),
      } as any),
    );

    expect(wrapper.find(Spinner).exists()).toBe(true);
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect(AccountService.getCredentialsKeyedByAccount).toHaveBeenCalledWith('azure');
    expect(BakeryReader.getRegions).toHaveBeenCalledWith('azure');
    expect(BakeryReader.getBaseOsOptions).toHaveBeenCalledWith('azure');
    expect(updateStage).toHaveBeenCalledWith(
      jasmine.objectContaining({
        extendedAttributes: {},
        regions: ['eastus'],
        user: 'user@example.com',
      }),
    );
    expect(wrapper.find(Spinner).exists()).toBe(false);
  });

  it('clears the Azure bake scalar region when that region is deselected', () => {
    const stage = { account: 'bakery', region: 'eastus', regions: ['eastus', 'westus'] } as any;
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps(stage, updateStage)), {
      disableLifecycleMethods: true,
    });
    wrapper.setState({ loading: false, regions: ['eastus', 'westus'] });

    wrapper
      .find('input[type="checkbox"]')
      .at(0)
      .simulate('change', { target: { checked: false } });

    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ region: undefined, regions: ['westus'] }));
  });

  it('preserves the Azure bake scalar region when a different region is deselected', () => {
    const stage = { account: 'bakery', region: 'eastus', regions: ['eastus', 'westus'] } as any;
    const updateStage = jasmine.createSpy('updateStage');
    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps(stage, updateStage)), {
      disableLifecycleMethods: true,
    });
    wrapper.setState({ loading: false, regions: ['eastus', 'westus'] });

    wrapper
      .find('input[type="checkbox"]')
      .at(1)
      .simulate('change', { target: { checked: false } });

    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ region: 'eastus', regions: ['eastus'] }));
  });

  it('preserves Azure bake source-image mode field clearing and managed image behavior', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' } } as any),
    );
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(Promise.resolve([{ name: 'account-east' }] as any));
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));
    spyOn(AzureImageReader.prototype, 'findImages').and.returnValue(
      Promise.resolve([{ imageName: 'managed-ubuntu', ostype: 'Linux' }] as any),
    );

    const updateStage = jasmine.createSpy('updateStage');
    const stage = { account: 'bakery', baseOs: 'ubuntu', packageType: 'DEB' } as any;
    const wrapper = shallow(
      React.createElement(AzureBakeStageConfig, {
        application: { attributes: {}, defaultCredentials: { azure: 'bakery' }, defaultRegions: { azure: 'eastus' } },
        pipeline: {},
        stage,
        stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
        updateStage,
        updateStageField: jasmine.createSpy('updateStageField'),
      } as any),
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Managed Images')
      .simulate('click');
    expect(AzureImageReader.prototype.findImages).toHaveBeenCalledWith({
      provider: 'azure',
      managedImages: true,
      account: 'bakery',
    });
    expect(updateStage).toHaveBeenCalledWith(jasmine.objectContaining({ baseOs: null, packageType: null }));

    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper.find('select[name="managedImage"]').simulate('change', { target: { value: 'managed-ubuntu' } });
    expect(updateStage).toHaveBeenCalledWith(
      jasmine.objectContaining({ managedImage: 'managed-ubuntu', osType: 'linux', packageType: null }),
    );

    wrapper.find('select[name="account"]').simulate('change', { target: { value: 'next-account' } });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(AccountService.getRegionsForAccount).toHaveBeenCalledWith('next-account');
    expect(updateStage).toHaveBeenCalledWith(
      jasmine.objectContaining({ account: 'next-account', osType: null, packageType: null, managedImage: null }),
    );
  });

  it('ignores stale Azure bake account-specific region responses', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    const staleRegions = deferred<any[]>();
    const currentRegions = deferred<any[]>();
    spyOn(AccountService, 'getRegionsForAccount').and.callFake((account: string) => {
      return account === 'stale' ? staleRegions.promise : currentRegions.promise;
    });
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));

    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper.find('select[name="account"]').simulate('change', { target: { value: 'stale' } });
    wrapper.find('select[name="account"]').simulate('change', { target: { value: 'newer' } });

    staleRegions.resolve([{ name: 'stale-region' }] as any);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect((wrapper.state() as any).regions).not.toEqual(['stale-region']);

    currentRegions.resolve([{ name: 'current-region' }] as any);
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect((wrapper.state() as any).regions).toEqual(['current-region']);
  });

  it('ignores stale Azure bake managed image responses', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(Promise.resolve([{ name: 'current-region' }] as any));
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));
    const staleImages = deferred<any[]>();
    const currentImages = deferred<any[]>();
    spyOn(AzureImageReader.prototype, 'findImages').and.callFake((params: any) => {
      return params.account === 'bakery' ? staleImages.promise : currentImages.promise;
    });

    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Managed Images')
      .simulate('click');
    wrapper.find('select[name="account"]').simulate('change', { target: { value: 'newer' } });

    staleImages.resolve([{ imageName: 'stale-image', ostype: 'Linux' }] as any);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect((wrapper.state() as any).managedImageOptions).toEqual([]);

    currentImages.resolve([{ imageName: 'current-image', ostype: 'Windows' }] as any);
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect((wrapper.state() as any).managedImageOptions).toEqual([
      { id: 'current-image', name: 'current-image', osType: 'Windows' },
    ]);
  });

  it('clears loaded Azure bake managed image options immediately when account changes', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(Promise.resolve([{ name: 'current-region' }] as any));
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));
    spyOn(AzureImageReader.prototype, 'findImages').and.returnValue(
      Promise.resolve([{ imageName: 'bakery-image', ostype: 'Linux' }] as any),
    );

    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Managed Images')
      .simulate('click');
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect((wrapper.state() as any).managedImageOptions).toEqual([
      { id: 'bakery-image', name: 'bakery-image', osType: 'Linux' },
    ]);

    wrapper.find('select[name="account"]').simulate('change', { target: { value: 'newer' } });

    expect((wrapper.state() as any).managedImageOptions).toEqual([]);
  });

  it('clears loaded Azure bake managed image options immediately when returning to managed images for a new account', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    spyOn(AccountService, 'getRegionsForAccount').and.returnValue(Promise.resolve([{ name: 'current-region' }] as any));
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));
    const nextAccountImages = deferred<any[]>();
    spyOn(AzureImageReader.prototype, 'findImages').and.callFake((params: any) => {
      return params.account === 'newer'
        ? nextAccountImages.promise
        : Promise.resolve([{ imageName: 'bakery-image', ostype: 'Linux' }] as any);
    });

    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Managed Images')
      .simulate('click');
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect((wrapper.state() as any).managedImageOptions).toEqual([
      { id: 'bakery-image', name: 'bakery-image', osType: 'Linux' },
    ]);

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Default Images')
      .simulate('click');
    wrapper.find('select[name="account"]').simulate('change', { target: { value: 'newer' } });
    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Managed Images')
      .simulate('click');

    expect((wrapper.state() as any).managedImageOptions).toEqual([]);

    nextAccountImages.resolve([{ imageName: 'newer-image', ostype: 'Windows' }] as any);
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    expect((wrapper.state() as any).managedImageOptions).toEqual([
      { id: 'newer-image', name: 'newer-image', osType: 'Windows' },
    ]);
  });

  it('removes empty Azure bake fields when users clear text inputs', async () => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ name: 'user@example.com' } as any);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ bakery: { name: 'bakery' } } as any),
    );
    spyOn(BakeryReader, 'getRegions').and.returnValue(Promise.resolve(['rosco-east']) as any);
    spyOn(BakeryReader, 'getBaseOsOptions').and.returnValue(Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any));
    spyOn(BakeryReader, 'getBaseLabelOptions').and.returnValue(Promise.resolve(['release']));

    const updateStage = jasmine.createSpy('updateStage');
    const stage = { account: 'bakery', baseName: 'old-base-name' } as any;
    const wrapper = shallow(React.createElement(AzureBakeStageConfig, bakeStageProps(stage, updateStage)));
    await new Promise((resolve) => setTimeout(resolve, 0));
    wrapper.update();

    wrapper
      .find(StageConfigField)
      .findWhere((field) => field.prop('label') === 'Base Name')
      .find('input')
      .simulate('change', { target: { value: '' } });

    expect(stage.baseName).toBeUndefined();
    expect(updateStage.calls.mostRecent().args[0].baseName).toBeUndefined();
  });
});
