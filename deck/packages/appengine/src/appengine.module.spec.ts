import { CloudProviderRegistry, DeploymentStrategyRegistry, Registry, TaskExecutor } from '@spinnaker/core';

import * as appengineEntrypoint from './index';
import { AppengineInstanceDetails } from './instance/details/AppengineInstanceDetails';
import {
  AppengineCreateLoadBalancerModal,
  updateAllocationLocatorType,
} from './loadBalancer/configure/AppengineCreateLoadBalancerModal';
import {
  AppengineLoadBalancerActions,
  AppengineLoadBalancerDetailsSection,
  canDeleteAppengineLoadBalancer,
  getDeleteLoadBalancerWarning,
  useAppengineLoadBalancerDetails,
} from './loadBalancer/details';
import { AppengineLoadBalancerTransformer } from './loadBalancer/transformer';
import { getServerGroupDisplayName } from './pipeline/stages/AppengineExecutionDetails';
import { getAppengineAccountRegion } from './pipeline/stages/AppengineServerGroupStageConfig';
import {
  AppengineCloneServerGroupModal,
  normalizeAppengineServerGroupCommandForSubmit,
} from './serverGroup/configure/wizard/AppengineCloneServerGroupModal';
import { AppengineServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import {
  AppengineServerGroupActions,
  appengineServerGroupDetailsGetter,
  AppengineServerGroupDetailsSection,
  canDestroyServerGroup,
  canDisableServerGroup,
  canStartServerGroup,
  canStopServerGroup,
  expectedAllocationsAfterDisableOperation,
} from './serverGroup/details';
import { AppengineServerGroupTransformer } from './serverGroup/transformer';
import { AppengineServerGroupWriter } from './serverGroup/writer/serverGroup.write.service';

describe('App Engine provider registration', () => {
  it('registers provider configuration from the package entrypoint without exporting an Angular module token', () => {
    expect(Object.prototype.hasOwnProperty.call(appengineEntrypoint, 'APPENGINE_MODULE')).toBe(false);

    expect(CloudProviderRegistry.getValue('appengine', 'serverGroup.transformer')).toBe(
      AppengineServerGroupTransformer,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'serverGroup.commandBuilder')).toBe(
      AppengineServerGroupCommandBuilder,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'loadBalancer.transformer')).toBe(
      AppengineLoadBalancerTransformer,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'instance.details')).toBe(AppengineInstanceDetails);
    expect(CloudProviderRegistry.getValue('appengine', 'serverGroup.detailsActions')).toBe(AppengineServerGroupActions);
    expect(CloudProviderRegistry.getValue('appengine', 'serverGroup.detailsGetter')).toBe(
      appengineServerGroupDetailsGetter,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'serverGroup.detailsSections')).toEqual([
      AppengineServerGroupDetailsSection,
    ]);
    expect(CloudProviderRegistry.getValue('appengine', 'serverGroup.CloneServerGroupModal')).toBe(
      AppengineCloneServerGroupModal,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'loadBalancer.useDetailsHook')).toBe(
      useAppengineLoadBalancerDetails,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'loadBalancer.detailsActions')).toBe(
      AppengineLoadBalancerActions,
    );
    expect(CloudProviderRegistry.getValue('appengine', 'loadBalancer.detailsSections')).toEqual([
      AppengineLoadBalancerDetailsSection,
    ]);
    expect(CloudProviderRegistry.getValue('appengine', 'loadBalancer.CreateLoadBalancerModal')).toBe(
      AppengineCreateLoadBalancerModal,
    );
    expect(DeploymentStrategyRegistry.listStrategies('appengine').map((strategy) => strategy.key)).toContain('custom');
  });

  it('registers App Engine pipeline stages with React components', () => {
    [
      'enableServerGroup',
      'disableServerGroup',
      'destroyServerGroup',
      'shrinkCluster',
      'startAppEngineServerGroup',
      'stopAppEngineServerGroup',
      'upsertAppEngineLoadBalancers',
      'deployAppEngineConfiguration',
    ].forEach((type) => {
      const stageConfig = Registry.pipeline.getStageConfig({ type, cloudProvider: 'appengine' } as any);
      expect(stageConfig).toBeDefined();
      expect(stageConfig.component).toBeDefined();
      expect(stageConfig[`template${'Url'}`]).toBeUndefined();
      expect(stageConfig.controller).toBeUndefined();
      expect(stageConfig[`executionDetails${'Url'}`]).toBeUndefined();
    });

    expect(
      Registry.pipeline
        .getStageConfig({ type: 'startAppEngineServerGroup', cloudProvider: 'appengine' } as any)
        .executionDetailsSections.map((section: any) => section.title),
    ).toEqual(['startServerGroupConfig', 'taskStatus']);
    expect(
      Registry.pipeline
        .getStageConfig({ type: 'stopAppEngineServerGroup', cloudProvider: 'appengine' } as any)
        .executionDetailsSections.map((section: any) => section.title),
    ).toEqual(['stopServerGroupConfig', 'taskStatus']);
    expect(
      Registry.pipeline
        .getStageConfig({ type: 'upsertAppEngineLoadBalancers', cloudProvider: 'appengine' } as any)
        .executionDetailsSections.map((section: any) => section.title),
    ).toEqual(['editLoadBalancerConfig', 'taskStatus']);
  });

  it('keeps start and stop actions eligible only for supported App Engine server groups', () => {
    expect(canStartServerGroup({ env: 'FLEXIBLE', servingStatus: 'STOPPED' } as any)).toBe(true);
    expect(
      canStartServerGroup({ env: 'STANDARD', scalingPolicy: { type: 'AUTOMATIC' }, servingStatus: 'STOPPED' } as any),
    ).toBe(false);
    expect(
      canStopServerGroup({ env: 'STANDARD', scalingPolicy: { type: 'BASIC' }, servingStatus: 'SERVING' } as any),
    ).toBe(true);
    expect(canStopServerGroup({ env: 'FLEXIBLE', servingStatus: 'STOPPED' } as any)).toBe(false);
  });

  it('keeps App Engine clone submissions on createServerGroup', () => {
    const executeTask = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}) as any);
    const writer = new AppengineServerGroupWriter();

    writer.cloneServerGroup(
      {
        application: 'myapp',
        credentials: 'test',
        region: 'europe-west1',
        viewState: { mode: 'clone' },
        configArtifacts: [],
        configFilepaths: [],
        configFiles: [],
        interestingHealthProviderNames: [],
      } as any,
      { name: 'myapp' } as any,
    );

    expect(executeTask.calls.mostRecent().args[0].job[0].type).toBe('createServerGroup');
  });

  it('keeps disable server group task ownership on the application object', () => {
    const executeTask = spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}) as any);
    const writer = new AppengineServerGroupWriter();
    const application = { name: 'myapp' } as any;

    writer.disableServerGroup({ name: 'myapp-v001', region: 'europe-west1', account: 'test' } as any, application);

    expect(executeTask.calls.mostRecent().args[0].application).toBe(application);
  });

  it('restores App Engine server group action eligibility from traffic allocations', () => {
    const serverGroup = { name: 'app-v001', disabled: false } as any;
    const loadBalancers = [{ split: { shardBy: 'IP', allocations: { 'app-v001': 0.4, 'app-v002': 0.6 } } }];

    expect(expectedAllocationsAfterDisableOperation(serverGroup, loadBalancers)).toEqual({ 'app-v002': 1 });
    expect(canDisableServerGroup(serverGroup, loadBalancers)).toBe(true);
    expect(canDestroyServerGroup(serverGroup, loadBalancers)).toBe(true);
    expect(canDisableServerGroup({ ...serverGroup, disabled: true }, loadBalancers)).toBe(false);
  });

  it('restores App Engine load balancer delete availability and warnings', () => {
    expect(canDeleteAppengineLoadBalancer({ name: 'default' })).toBe(false);
    expect(canDeleteAppengineLoadBalancer({ name: 'service-a' })).toBe(true);
    expect(getDeleteLoadBalancerWarning({ name: 'service-a', serverGroups: [{ name: 'service-a-v001' }] })).toContain(
      'will destroy <b>service-a-v001</b>',
    );
  });

  it('defaults App Engine clone command arrays before submit', () => {
    const command = normalizeAppengineServerGroupCommandForSubmit({ viewState: { mode: 'clone' } } as any);

    expect(command.configArtifacts).toEqual([]);
    expect(command.configFilepaths).toEqual([]);
    expect(command.configFiles).toEqual([]);
    expect(command.interestingHealthProviderNames).toEqual([]);
    expect(command.fromArtifact).toBe(false);
    expect(command.sourceType).toBe('git');
  });

  it('keeps pipeline load balancer allocations able to target dynamic coordinates', () => {
    const allocation = updateAllocationLocatorType(
      { allocation: 50, locatorType: 'text', serverGroupName: '${sg}' },
      'targetCoordinate',
    );

    expect(allocation.locatorType).toBe('targetCoordinate');
    expect(allocation.serverGroupName).toBeUndefined();
    expect(allocation.target).toBe('current_asg_dynamic');
  });

  it('resolves App Engine stage region from selected account and shows ad-hoc server group names', () => {
    expect(getAppengineAccountRegion([{ name: 'prod', region: 'europe-west1' }] as any, 'prod')).toBe('europe-west1');
    expect(getServerGroupDisplayName({ serverGroupName: 'my-app-v001' })).toBe('my-app-v001');
    expect(getServerGroupDisplayName({ cluster: 'my-app', serverGroupName: 'my-app-v001' })).toBe('my-app');
  });
});
