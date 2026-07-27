import { mount, shallow } from 'enzyme';
import React from 'react';

import { AccountRegionClusterSelector, AccountService, ChecklistInput, StageConstants } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { awsModifyScalingProcessStage } from './modifyScalingProcessStage';

describe('AWS Modify Scaling Process stage', () => {
  function renderEditor(stage: any = {}, pipeline: any = {}) {
    const updateStageField = jasmine.createSpy('updateStageField');
    const updateStage = jasmine.createSpy('updateStage');
    const StageConfig = awsModifyScalingProcessStage.component;
    const stageModel = { type: 'modifyAwsScalingProcess', cloudProviderType: 'aws', ...stage };
    const wrapper = shallow(
      <StageConfig
        {...({
          application: { defaultCredentials: {}, defaultRegions: {}, getDataSource: () => ({ data: [] }) },
          pipeline,
          stage: stageModel,
          updateStage,
          updateStageField,
        } as any)}
      />,
    );

    return { stage: stageModel, updateStage, updateStageField, wrapper };
  }

  it('registers a dedicated stage editor', () => {
    expect(awsModifyScalingProcessStage.component).not.toBe(AmazonStageConfig);
  });

  it('renders the AWS server group selectors for a pipeline stage', () => {
    const { wrapper } = renderEditor({ target: 'current_asg' });
    const target = wrapper.find('select[name="target"]');

    expect(wrapper.find(AccountRegionClusterSelector).exists()).toBe(true);
    expect(target.exists()).toBe(true);
    if (!wrapper.find(AccountRegionClusterSelector).exists() || !target.exists()) {
      return;
    }
    expect(target.find('option').map((option) => option.prop('value'))).toEqual(
      StageConstants.TARGET_LIST.map((option) => option.val),
    );
    expect(target.prop('value')).toBe('current_asg');
  });

  it('reports account, region, and cluster changes from an unmutated stage', () => {
    const { stage, updateStage, updateStageField, wrapper } = renderEditor({
      credentials: 'test',
      regions: ['eu-west-1'],
      cluster: 'app-main',
    });
    const selector = wrapper.find(AccountRegionClusterSelector);
    const selectorStage = selector.prop('component');

    expect(selectorStage).not.toBe(stage);
    selectorStage.credentials = 'prod';
    selectorStage.regions = ['us-east-1'];
    selectorStage.cluster = 'app-prod';
    selector.prop('onComponentUpdate')(selectorStage);

    expect(stage).toEqual(
      jasmine.objectContaining({ credentials: 'test', regions: ['eu-west-1'], cluster: 'app-main' }),
    );
    expect(updateStage).toHaveBeenCalledWith(
      jasmine.objectContaining({ credentials: 'prod', regions: ['us-east-1'], cluster: 'app-prod' }),
    );
    expect(updateStageField).not.toHaveBeenCalled();
  });

  it('uses the AWS scaling process names and preserves process arrays', () => {
    const processes = ['Launch', 'AZRebalance'];
    const nextProcesses = ['Terminate', 'ScheduledActions'];
    const { updateStageField, wrapper } = renderEditor({ processes });
    const checklist = wrapper.find(ChecklistInput).filterWhere((input) => input.prop('name') === 'processes');

    expect(checklist.exists()).toBe(true);
    if (!checklist.exists()) {
      return;
    }
    expect(checklist.prop('stringOptions')).toEqual([
      'Launch',
      'Terminate',
      'AddToLoadBalancer',
      'AlarmNotification',
      'AZRebalance',
      'HealthCheck',
      'ReplaceUnhealthy',
      'ScheduledActions',
    ]);
    expect(checklist.prop('value')).toBe(processes);

    checklist.prop('onChange')({ target: { value: nextProcesses } } as any);

    expect(updateStageField).toHaveBeenCalledWith({ processes: nextProcesses });
  });

  it('supports suspend and resume while clearing only legacy action fields', () => {
    const processes = ['Launch', 'HealthCheck'];
    const stage = {
      action: 'suspend',
      processes,
      suspendProcesses: ['Terminate'],
      resumeProcesses: ['AZRebalance'],
    };
    const { updateStageField, wrapper } = renderEditor(stage);
    const action = wrapper.find('select[name="action"]');

    expect(action.exists()).toBe(true);
    if (!action.exists()) {
      return;
    }
    expect(action.find('option').map((option) => [option.prop('value'), option.text()])).toEqual([
      ['suspend', 'Suspend'],
      ['resume', 'Resume'],
    ]);

    action.simulate('change', { target: { value: 'resume' } });

    const changes = updateStageField.calls.mostRecent().args[0];
    expect(changes).toEqual({ action: 'resume', suspendProcesses: undefined, resumeProcesses: undefined });
    expect(Object.prototype.hasOwnProperty.call(changes, 'processes')).toBe(false);
    expect(stage.processes).toBe(processes);
  });

  it('normalizes legacy process fields when reopening without changing action', () => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([]));
    const processes = ['Launch', 'ScheduledActions'];
    const stage = {
      type: 'modifyAwsScalingProcess',
      cloudProvider: 'aws',
      cloudProviderType: 'aws',
      credentials: 'test',
      regions: ['eu-west-1'],
      cluster: 'app-main',
      target: 'current_asg',
      action: 'suspend',
      processes,
      suspendProcesses: ['Terminate'],
      resumeProcesses: ['AZRebalance'],
    };
    const updateStageField = jasmine.createSpy('updateStageField');
    const StageConfig = awsModifyScalingProcessStage.component;
    const wrapper = mount(
      <StageConfig
        {...({
          application: { defaultCredentials: {}, defaultRegions: {} },
          pipeline: { strategy: true },
          stage,
          updateStage: jasmine.createSpy('updateStage'),
          updateStageField,
        } as any)}
      />,
    );

    expect(updateStageField).toHaveBeenCalledWith({
      suspendProcesses: undefined,
      resumeProcesses: undefined,
    });
    expect(Object.prototype.hasOwnProperty.call(updateStageField.calls.mostRecent().args[0], 'processes')).toBe(false);
    expect(stage.processes).toBe(processes);

    wrapper.unmount();
  });
});
