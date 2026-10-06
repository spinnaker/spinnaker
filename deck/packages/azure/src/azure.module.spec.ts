import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import {
  AccountService,
  AuthenticationService,
  BakeryReader,
  CloudProviderRegistry,
  ExecutionDetailsTasks,
  Registry,
  SETTINGS,
} from '@spinnaker/core';
import { getFormGroupByLabel, renderWithRouter } from '../../core/src/utils/testUtils/rtl';

import './index';
import { AzureImageReader } from './image/image.reader';
import { AzureInstanceTypeService } from './instance/azureInstanceType.service';
import { AzureLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import { AzureBakeStageConfig } from './pipeline/stages/bake/azureBakeStage';
import {
  AzureDestroyAsgExecutionLabel,
  AzureDestroyAsgStageConfig,
} from './pipeline/stages/destroyAsg/azureDestroyAsgStage';
import {
  AzureDisableAsgExecutionLabel,
  AzureDisableAsgStageConfig,
} from './pipeline/stages/disableAsg/azureDisableAsgStage';
import {
  AzureEnableAsgExecutionLabel,
  AzureEnableAsgStageConfig,
} from './pipeline/stages/enableAsg/azureEnableAsgStage';
import { registerAzurePipelineStages } from './azure.module';
import { AzureSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { AzureSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { AzureServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AzureServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { AzureServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('Azure package registration', () => {
  function expectRegistered(path: string): void {
    expect(CloudProviderRegistry.getValue('azure', path), path).not.toBeNull();
  }

  function expectNonEmptyRegistration(path: string): void {
    const value = CloudProviderRegistry.getValue('azure', path);
    const entries = Array.isArray(value) ? value : [];
    expect(Array.isArray(value), path).toBe(true);
    expect(entries.length, path).toBeGreaterThan(0);
  }

  function expectStageComponent(stageTypes: any[], provides: string, component: any): any {
    const stage = stageTypes.find((candidate) => candidate.provides === provides);
    expect(stage, `azure ${provides} stage`).toBeDefined();
    expect(stage?.component, `azure ${provides} stage component`).toBe(component);
    return stage;
  }

  function expectRequiredFields(stageConfig: any, expectedFields: string[]): void {
    const requiredFields = stageConfig.validators
      .filter((validator: any) => validator.type === 'requiredField')
      .map((validator: any) => validator.fieldName);
    expect(requiredFields, `azure ${stageConfig.provides} required fields`).toEqual(expectedFields);
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

  function bakeStageProps(stage: any, updateStage = vi.fn()): any {
    return {
      application: { attributes: {}, defaultCredentials: { azure: 'bakery' }, defaultRegions: { azure: 'eastus' } },
      pipeline: {},
      stage,
      stageFieldUpdated: vi.fn(),
      updateStage,
      updateStageField: vi.fn(),
    };
  }

  function mockBakeOptions(regions = ['eastus', 'westus']): void {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({ bakery: { name: 'bakery' } } as any);
    vi.spyOn(BakeryReader, 'getRegions').mockResolvedValue(regions as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockResolvedValue({ baseImages: [{ id: 'ubuntu' }] } as any);
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockResolvedValue(['release']);
  }

  function applicationWithServerGroups(serverGroups: any[]): any {
    return {
      attributes: {},
      defaultCredentials: { azure: 'prod' },
      defaultRegions: { azure: 'eastus' },
      getDataSource: (key: string) => (key === 'serverGroups' ? { data: serverGroups } : { data: [] }),
    };
  }

  it('registers Azure provider values', () => {
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
  });

  it('registers Azure pipeline stages with React components', () => {
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

      expectRequiredFields(bakeStage, ['package', 'regions']);
      expect(bakeStage.executionDetailsSections).toBeDefined();
      expect(bakeStage.executionDetailsSections[1]).toBe(ExecutionDetailsTasks);

      [destroyStage, disableStage, enableStage].forEach((stage) => {
        expectRequiredFields(stage, ['cluster', 'target', 'regions', 'credentials']);
      });
      [
        [destroyStage, AzureDestroyAsgExecutionLabel, 'Destroy Server Group'],
        [disableStage, AzureDisableAsgExecutionLabel, 'Disable Server Group'],
        [enableStage, AzureEnableAsgExecutionLabel, 'Enable Server Group'],
      ].forEach(([stage, expectedComponent, action]: any[]) => {
        expect(stage.executionLabelComponent).toBe(expectedComponent);
        const label = render(
          React.createElement(stage.executionLabelComponent, {
            stage: { masterStage: { context: { region: 'eastus', serverGroupName: 'azureapp-v001' } } },
          }),
        );
        expect(label.container).toHaveTextContent(`${action}: azureapp-v001 (eastus)`);
        label.unmount();
      });
    } finally {
      Registry.pipeline = previousPipelineRegistry;
      Registry.urlBuilder = previousUrlBuilderRegistry;
    }
  });

  it('renders Azure bake execution details', () => {
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
      renderWithRouter(
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

      expect((BakeExecutionDetails as any).title).toBe('bakeConfig');
      expect(screen.getByText('Azure')).toBeInTheDocument();
      expect(screen.getByText('azure-image-v001')).toBeInTheDocument();
      expect(screen.getByText('my-package')).toBeInTheDocument();
      expect(screen.getByText('template.json')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'View Bakery Details' })).toHaveAttribute(
        'href',
        '/bakery/eastus/bake-123',
      );
    } finally {
      SETTINGS.bakeryDetailUrl = previousBakeryDetailUrl;
      Registry.pipeline = previousPipelineRegistry;
      Registry.urlBuilder = previousUrlBuilderRegistry;
    }
  });

  it('loads destroy stage region from selected Azure account details', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'test-account' }] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue(['westus'] as any);
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(Promise.resolve({ org: 'westus' } as any));

    const updateStageField = vi.fn();
    const stage = { credentials: 'test-account' };

    render(
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

    expect(AccountService.getAccountDetails).toHaveBeenCalledWith('test-account');
    await waitFor(() => expect(updateStageField).toHaveBeenCalledWith({ regions: ['westus'] }));
  });

  it('renders account, region, and cluster selectors for Azure server group stages', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(
      Promise.resolve([{ name: 'prod' }, { name: 'test' }] as any),
    );
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockReturnValue(
      Promise.resolve(['eastus', 'westus']) as any,
    );
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(Promise.resolve({} as any));

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
      const updateStageField = vi.fn();
      const stage = {
        cloudProvider: 'azure',
        cluster: 'app',
        credentials: 'prod',
        moniker: { app: 'app', cluster: 'app', sequence: 1 },
        regions: ['eastus', 'westus'],
      } as any;
      const rendered = render(
        React.createElement(component, {
          application,
          pipeline: {},
          stage,
          updateStageField,
        }),
      );

      const accountField = await waitFor(() => getFormGroupByLabel('Account', rendered.container));
      const accountSelect = within(accountField).getByRole('combobox');
      expect(within(accountSelect).getByRole('option', { name: 'prod' })).toBeInTheDocument();

      const eastusCheckbox = screen.getByRole('checkbox', { name: 'eastus' });
      const westusCheckbox = screen.getByRole('checkbox', { name: 'westus' });
      const clusterSelect = within(getFormGroupByLabel('Cluster', rendered.container)).getByRole('combobox');
      expect(within(clusterSelect).getByRole('option', { name: 'app' })).toBeInTheDocument();

      fireEvent.change(clusterSelect, { target: { value: 'api' } });
      expect(updateStageField, `${component.name} cluster update sets moniker`).toHaveBeenCalledWith(
        expect.objectContaining({
          cluster: 'api',
          moniker: expect.objectContaining({ cluster: 'api', sequence: null }),
        }),
      );

      fireEvent.click(eastusCheckbox);
      fireEvent.click(westusCheckbox);
      expect(updateStageField, `${component.name} region update clears cluster`).toHaveBeenCalledWith(
        expect.objectContaining({ cluster: undefined, moniker: undefined, regions: [] }),
      );

      fireEvent.change(accountSelect, { target: { value: 'test' } });
      expect(updateStageField, `${component.name} account update clears dependent fields`).toHaveBeenCalledWith(
        expect.objectContaining({ credentials: 'test', cluster: undefined, moniker: undefined, regions: [] }),
      );

      const targetSelect = within(getFormGroupByLabel('Target', rendered.container)).getByRole('combobox');
      fireEvent.change(targetSelect, { target: { value: 'oldest_asg_dynamic' } });
      expect(updateStageField).toHaveBeenCalledWith({ target: 'oldest_asg_dynamic' });
      rendered.unmount();
    }
  });

  it('preserves selected Azure cluster while selected regions still include that cluster', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(Promise.resolve([{ name: 'prod' }] as any));
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockReturnValue(
      Promise.resolve(['eastus', 'westus']) as any,
    );

    const updateStageField = vi.fn();
    const stage = {
      cloudProvider: 'azure',
      cluster: 'app',
      credentials: 'prod',
      moniker: { app: 'app', cluster: 'app', sequence: 1 },
      regions: ['eastus', 'westus'],
    } as any;
    const rendered = render(
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

    const westusCheckbox = await screen.findByRole('checkbox', { name: 'westus' });
    fireEvent.click(westusCheckbox);

    expect(updateStageField).toHaveBeenCalledWith({ regions: ['eastus'] });
    expect(updateStageField).not.toHaveBeenCalledWith(
      expect.objectContaining({ cluster: undefined, moniker: undefined }),
    );

    rendered.unmount();
  });

  it('supports free-text Azure cluster entry when the selected cluster is not discovered', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(Promise.resolve([{ name: 'prod' }] as any));
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockReturnValue(
      Promise.resolve(['eastus', 'westus']) as any,
    );

    const updateStageField = vi.fn();
    const stage = {
      cloudProvider: 'azure',
      cluster: 'custom-cluster',
      credentials: 'prod',
      regions: ['eastus'],
    } as any;
    const rendered = render(
      React.createElement(AzureDisableAsgStageConfig, {
        application: applicationWithServerGroups([]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    const clusterInput = await within(getFormGroupByLabel('Cluster', rendered.container)).findByRole('textbox');
    expect(clusterInput).toHaveValue('custom-cluster');

    fireEvent.change(clusterInput, { target: { value: 'new-custom-cluster' } });
    expect(updateStageField).toHaveBeenCalledWith({ cluster: 'new-custom-cluster', moniker: undefined });

    fireEvent.click(screen.getByText(/list of existing clusters/));
    expect(updateStageField).toHaveBeenCalledWith({ cluster: undefined, moniker: undefined });

    rendered.unmount();
  });

  it('preserves existing Azure health override selections on new disable and enable stages', () => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'test-account' }] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue(['eastus'] as any);
    [AzureDisableAsgStageConfig, AzureEnableAsgStageConfig].forEach((component) => {
      const stage = { isNew: true, interestingHealthProviderNames: ['azureService'] } as any;
      const updateStageField = vi.fn();

      const rendered = render(
        React.createElement(component, {
          application: {
            attributes: { platformHealthOnlyShowOverride: true },
            defaultCredentials: { azure: 'test-account' },
            defaultRegions: { azure: 'eastus' },
          },
          stage,
          updateStageField,
        }),
      );

      expect(screen.getByRole('checkbox', { name: 'Consider only azureService health' })).toBeChecked();
      expect(stage.interestingHealthProviderNames).toEqual(['azureService']);
      updateStageField.mockClear();
      fireEvent.click(screen.getByRole('checkbox', { name: 'Consider only azureService health' }));
      rendered.rerender(
        React.createElement(component, {
          application: {
            attributes: { platformHealthOnlyShowOverride: true },
            defaultCredentials: { azure: 'test-account' },
            defaultRegions: { azure: 'eastus' },
          },
          stage,
          updateStageField,
        }),
      );
      fireEvent.click(screen.getByRole('checkbox', { name: 'Consider only azureService health' }));
      expect(stage.interestingHealthProviderNames).toEqual(['azureService']);
      expect(updateStageField.mock.calls).toEqual([
        [{ interestingHealthProviderNames: null }],
        [{ interestingHealthProviderNames: ['azureService'] }],
      ]);
      rendered.unmount();

      const hidden = render(
        React.createElement(component, {
          application: {
            attributes: { platformHealthOnlyShowOverride: false },
            defaultCredentials: { azure: 'test-account' },
            defaultRegions: { azure: 'eastus' },
          },
          stage: { isNew: true },
          updateStageField: vi.fn(),
        }),
      );
      expect(screen.queryByRole('checkbox', { name: 'Consider only azureService health' })).not.toBeInTheDocument();
      hidden.unmount();
    });
  });

  it('ignores stale destroy account detail responses and clears regions when credentials change', async () => {
    const firstAccount = deferred<any>();
    const secondAccount = deferred<any>();
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(
      Promise.resolve([{ name: 'first-account' }, { name: 'second-account' }] as any),
    );
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockReturnValue(
      Promise.resolve(['first-region', 'second-region']) as any,
    );
    vi.spyOn(AccountService, 'getAccountDetails').mockImplementation((account: string) => {
      return account === 'first-account' ? firstAccount.promise : secondAccount.promise;
    });

    const updateStageField = vi.fn();
    const stage = { credentials: 'first-account', regions: ['stale-region'] } as any;
    const rendered = render(
      React.createElement(AzureDestroyAsgStageConfig, {
        application: applicationWithServerGroups([]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    const accountSelect = within(await waitFor(() => getFormGroupByLabel('Account', rendered.container))).getByRole(
      'combobox',
    );
    fireEvent.change(accountSelect, { target: { value: 'second-account' } });

    expect(updateStageField).toHaveBeenCalledWith(
      expect.objectContaining({ credentials: 'second-account', regions: [] }),
    );

    firstAccount.resolve({ org: 'first-region' });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(updateStageField).not.toHaveBeenCalledWith({ regions: ['first-region'] });

    secondAccount.resolve({ org: 'second-region' });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(updateStageField).toHaveBeenCalledWith({ regions: ['second-region'] });
    rendered.unmount();
  });

  it('leaves destroy regions empty when account detail loading fails after credentials change', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockReturnValue(
      Promise.resolve([{ name: 'first-account' }, { name: 'bad-account' }] as any),
    );
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockReturnValue(Promise.resolve(['eastus']) as any);
    vi.spyOn(AccountService, 'getAccountDetails').mockReturnValue(Promise.reject(new Error('boom')));

    const updateStageField = vi.fn();
    const stage = { credentials: 'first-account', regions: ['stale-region'] } as any;
    const rendered = render(
      React.createElement(AzureDestroyAsgStageConfig, {
        application: applicationWithServerGroups([]),
        pipeline: {},
        stage,
        updateStageField,
      }),
    );

    const accountSelect = within(await waitFor(() => getFormGroupByLabel('Account', rendered.container))).getByRole(
      'combobox',
    );
    fireEvent.change(accountSelect, { target: { value: 'bad-account' } });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(stage.regions).toEqual([]);
    expect(updateStageField).toHaveBeenCalledWith(expect.objectContaining({ credentials: 'bad-account', regions: [] }));
    rendered.unmount();
  });

  it('initializes Azure bake options and defaults from services', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' } } as any),
    );
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east', 'rosco-west']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu', shortDescription: 'Ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release', 'candidate']));

    const updateStage = vi.fn();
    const rendered = render(
      React.createElement(AzureBakeStageConfig, {
        application: { attributes: {}, defaultCredentials: { azure: 'bakery' }, defaultRegions: { azure: 'eastus' } },
        pipeline: {},
        stage: { package: 'my-package' },
        stageFieldUpdated: vi.fn(),
        updateStage,
        updateStageField: vi.fn(),
      } as any),
    );

    expect(screen.queryByText('Account')).not.toBeInTheDocument();

    expect(AccountService.getCredentialsKeyedByAccount).toHaveBeenCalledWith('azure');
    expect(BakeryReader.getRegions).toHaveBeenCalledWith('azure');
    expect(BakeryReader.getBaseOsOptions).toHaveBeenCalledWith('azure');
    await waitFor(() =>
      expect(updateStage).toHaveBeenCalledWith(
        expect.objectContaining({
          extendedAttributes: {},
          regions: ['eastus'],
          user: 'user@example.com',
        }),
      ),
    );
    await waitFor(() => getFormGroupByLabel('Account', rendered.container));
    ['Account', 'Regions', 'Base OS', 'Package', 'Base Label', 'Base Name'].forEach((label) =>
      expect(getFormGroupByLabel(label, rendered.container)).toBeInTheDocument(),
    );
  });

  it('clears the Azure bake scalar region when that region is deselected', async () => {
    mockBakeOptions();
    const stage = { account: 'bakery', region: 'eastus', regions: ['eastus', 'westus'] } as any;
    const updateStage = vi.fn();
    render(React.createElement(AzureBakeStageConfig, bakeStageProps(stage, updateStage)));

    fireEvent.click(await screen.findByRole('checkbox', { name: 'eastus' }));

    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ region: undefined, regions: ['westus'] }));
  });

  it('preserves the Azure bake scalar region when a different region is deselected', async () => {
    mockBakeOptions();
    const stage = { account: 'bakery', region: 'eastus', regions: ['eastus', 'westus'] } as any;
    const updateStage = vi.fn();
    render(React.createElement(AzureBakeStageConfig, bakeStageProps(stage, updateStage)));

    fireEvent.click(await screen.findByRole('checkbox', { name: 'westus' }));

    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ region: 'eastus', regions: ['eastus'] }));
  });

  it('preserves Azure bake source-image mode field clearing and managed image behavior', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, 'next-account': { name: 'next-account' } } as any),
    );
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(
      Promise.resolve([{ name: 'account-east' }] as any),
    );
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));
    vi.spyOn(AzureImageReader.prototype, 'findImages').mockReturnValue(
      Promise.resolve([{ imageName: 'managed-ubuntu', ostype: 'Linux' }] as any),
    );

    const updateStage = vi.fn();
    const stage = { account: 'bakery', baseOs: 'ubuntu', packageType: 'DEB' } as any;
    const rendered = render(
      React.createElement(AzureBakeStageConfig, {
        application: { attributes: {}, defaultCredentials: { azure: 'bakery' }, defaultRegions: { azure: 'eastus' } },
        pipeline: {},
        stage,
        stageFieldUpdated: vi.fn(),
        updateStage,
        updateStageField: vi.fn(),
      } as any),
    );

    fireEvent.click(await screen.findByRole('button', { name: 'Managed Images' }));
    expect(AzureImageReader.prototype.findImages).toHaveBeenCalledWith({
      provider: 'azure',
      managedImages: true,
      account: 'bakery',
    });
    expect(updateStage).toHaveBeenCalledWith(expect.objectContaining({ baseOs: null, packageType: null }));

    const managedImageSelect = await waitFor(() => {
      const select = within(getFormGroupByLabel('Managed Image', rendered.container)).getByRole('combobox');
      expect(within(select).getByRole('option', { name: 'managed-ubuntu' })).toBeInTheDocument();
      return select;
    });
    fireEvent.change(managedImageSelect, { target: { value: 'managed-ubuntu' } });
    expect(updateStage).toHaveBeenCalledWith(
      expect.objectContaining({ managedImage: 'managed-ubuntu', osType: 'linux', packageType: null }),
    );

    fireEvent.change(within(getFormGroupByLabel('Account', rendered.container)).getByRole('combobox'), {
      target: { value: 'next-account' },
    });

    expect(AccountService.getRegionsForAccount).toHaveBeenCalledWith('next-account');
    expect(updateStage).toHaveBeenCalledWith(
      expect.objectContaining({ account: 'next-account', osType: null, packageType: null, managedImage: null }),
    );
  });

  it('ignores stale Azure bake account-specific region responses', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    const staleRegions = deferred<any[]>();
    const currentRegions = deferred<any[]>();
    vi.spyOn(AccountService, 'getRegionsForAccount').mockImplementation((account: string) => {
      return account === 'stale' ? staleRegions.promise : currentRegions.promise;
    });
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));

    const rendered = render(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    await screen.findByRole('option', { name: 'newer' });
    const accountSelect = within(getFormGroupByLabel('Account', rendered.container)).getByRole('combobox');

    fireEvent.change(accountSelect, { target: { value: 'stale' } });
    fireEvent.change(accountSelect, { target: { value: 'newer' } });

    staleRegions.resolve([{ name: 'stale-region' }] as any);
    await Promise.resolve();
    expect(screen.queryByRole('checkbox', { name: 'stale-region' })).not.toBeInTheDocument();

    currentRegions.resolve([{ name: 'current-region' }] as any);
    expect(await screen.findByRole('checkbox', { name: 'current-region' })).toBeInTheDocument();
    rendered.unmount();
  });

  it('ignores stale Azure bake managed image responses', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(
      Promise.resolve([{ name: 'current-region' }] as any),
    );
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));
    const staleImages = deferred<any[]>();
    const currentImages = deferred<any[]>();
    vi.spyOn(AzureImageReader.prototype, 'findImages').mockImplementation((params: any) => {
      return params.account === 'bakery' ? staleImages.promise : currentImages.promise;
    });

    const rendered = render(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    fireEvent.click(await screen.findByRole('button', { name: 'Managed Images' }));
    fireEvent.change(within(getFormGroupByLabel('Account', rendered.container)).getByRole('combobox'), {
      target: { value: 'newer' },
    });

    staleImages.resolve([{ imageName: 'stale-image', ostype: 'Linux' }] as any);
    await Promise.resolve();
    expect(screen.queryByRole('option', { name: 'stale-image' })).not.toBeInTheDocument();

    currentImages.resolve([{ imageName: 'current-image', ostype: 'Windows' }] as any);
    expect(await screen.findByRole('option', { name: 'current-image' })).toBeInTheDocument();
  });

  it('clears loaded Azure bake managed image options immediately when account changes', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(
      Promise.resolve([{ name: 'current-region' }] as any),
    );
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));
    vi.spyOn(AzureImageReader.prototype, 'findImages').mockReturnValue(
      Promise.resolve([{ imageName: 'bakery-image', ostype: 'Linux' }] as any),
    );

    const rendered = render(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    fireEvent.click(await screen.findByRole('button', { name: 'Managed Images' }));
    expect(await screen.findByRole('option', { name: 'bakery-image' })).toBeInTheDocument();

    fireEvent.change(within(getFormGroupByLabel('Account', rendered.container)).getByRole('combobox'), {
      target: { value: 'newer' },
    });

    expect(screen.queryByRole('option', { name: 'bakery-image' })).not.toBeInTheDocument();
  });

  it('clears loaded Azure bake managed image options immediately when returning to managed images for a new account', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' }, newer: { name: 'newer' } } as any),
    );
    vi.spyOn(AccountService, 'getRegionsForAccount').mockReturnValue(
      Promise.resolve([{ name: 'current-region' }] as any),
    );
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));
    const nextAccountImages = deferred<any[]>();
    vi.spyOn(AzureImageReader.prototype, 'findImages').mockImplementation((params: any) => {
      return params.account === 'newer'
        ? nextAccountImages.promise
        : Promise.resolve([{ imageName: 'bakery-image', ostype: 'Linux' }] as any);
    });

    const rendered = render(React.createElement(AzureBakeStageConfig, bakeStageProps({ account: 'bakery' })));
    fireEvent.click(await screen.findByRole('button', { name: 'Managed Images' }));
    expect(await screen.findByRole('option', { name: 'bakery-image' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Default Images' }));
    fireEvent.change(within(getFormGroupByLabel('Account', rendered.container)).getByRole('combobox'), {
      target: { value: 'newer' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Managed Images' }));

    expect(screen.queryByRole('option', { name: 'bakery-image' })).not.toBeInTheDocument();

    nextAccountImages.resolve([{ imageName: 'newer-image', ostype: 'Windows' }] as any);
    expect(await screen.findByRole('option', { name: 'newer-image' })).toBeInTheDocument();
  });

  it('removes empty Azure bake fields when users clear text inputs', async () => {
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ name: 'user@example.com' } as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(
      Promise.resolve({ bakery: { name: 'bakery' } } as any),
    );
    vi.spyOn(BakeryReader, 'getRegions').mockReturnValue(Promise.resolve(['rosco-east']) as any);
    vi.spyOn(BakeryReader, 'getBaseOsOptions').mockReturnValue(
      Promise.resolve({ baseImages: [{ id: 'ubuntu' }] } as any),
    );
    vi.spyOn(BakeryReader, 'getBaseLabelOptions').mockReturnValue(Promise.resolve(['release']));

    const updateStage = vi.fn();
    const stage = { account: 'bakery', baseName: 'old-base-name' } as any;
    const rendered = render(React.createElement(AzureBakeStageConfig, bakeStageProps(stage, updateStage)));
    await screen.findByRole('button', { name: 'Managed Images' });
    fireEvent.change(within(getFormGroupByLabel('Base Name', rendered.container)).getByRole('textbox'), {
      target: { value: '' },
    });

    expect(stage.baseName).toBeUndefined();
    expect(updateStage.mock.lastCall[0].baseName).toBeUndefined();
  });
});
