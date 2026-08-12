import { shallow } from 'enzyme';
import React from 'react';

import { AccountRegionClusterSelector, AccountService, StageConstants } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { awsModifyWarmPoolStage } from './modifyWarmPoolStage';

describe('AWS Modify Warm Pool stage', () => {
  function renderEditor(stage: any = {}, pipeline: any = {}) {
    const updateStageField = jasmine.createSpy('updateStageField');
    const updateStage = jasmine.createSpy('updateStage');
    const StageConfig = awsModifyWarmPoolStage.component;
    const stageModel = { type: 'modifyWarmPool', cloudProviderType: 'aws', ...stage };
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

  beforeEach(() => {
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([]));
  });

  it('registers a dedicated stage editor', () => {
    expect(awsModifyWarmPoolStage.component).not.toBe(AmazonStageConfig);
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

  it('defaults the action to upsert and shows upsert-only fields', () => {
    const { wrapper } = renderEditor();
    const action = wrapper.find('select[name="action"]');

    expect(action.prop('value')).toBe('upsert');
    expect(wrapper.find('input[name="minSize"]').exists()).toBe(true);
    expect(wrapper.find('input[name="maxGroupPreparedCapacity"]').exists()).toBe(true);
    expect(wrapper.find('select[name="poolState"]').exists()).toBe(true);
  });

  it('hides upsert-only fields when action is delete', () => {
    const { wrapper } = renderEditor({ action: 'delete' });

    expect(wrapper.find('input[name="minSize"]').exists()).toBe(false);
    expect(wrapper.find('input[name="maxGroupPreparedCapacity"]').exists()).toBe(false);
    expect(wrapper.find('select[name="poolState"]').exists()).toBe(false);
  });

  it('updates the action field on change', () => {
    const { updateStageField, wrapper } = renderEditor({ action: 'upsert' });
    const action = wrapper.find('select[name="action"]');

    action.simulate('change', { target: { value: 'delete' } });

    expect(updateStageField).toHaveBeenCalledWith({ action: 'delete' });
  });

  it('updates warm pool fields on change', () => {
    const { updateStageField, wrapper } = renderEditor({ action: 'upsert' });

    wrapper.find('input[name="minSize"]').simulate('change', { target: { value: '3' } });
    expect(updateStageField).toHaveBeenCalledWith({ minSize: 3 });

    wrapper.find('select[name="poolState"]').simulate('change', { target: { value: 'Running' } });
    expect(updateStageField).toHaveBeenCalledWith({ poolState: 'Running' });
  });
});
