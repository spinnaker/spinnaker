import { mount } from 'enzyme';
import React from 'react';

import { AccountRegionClusterSelector, AccountService, PlatformHealthOverride, TargetSelect } from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';

import { awsResizeAsgStage } from './awsResizeAsgStage';

describe('AWS Resize Server Group stage', () => {
  beforeEach(() => {
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(Promise.resolve([]));
    spyOn(AccountService, 'listAccounts').and.returnValue(Promise.resolve([]));
  });

  function renderStage(stageOverrides: Record<string, any> = {}, applicationOverrides: Record<string, any> = {}) {
    const stage = {
      action: 'scale_up',
      capacity: {},
      resizeType: 'pct',
      scalePct: 150,
      target: 'current_asg',
      targetHealthyDeployPercentage: 100,
      ...stageOverrides,
    };
    const application = {
      attributes: { platformHealthOnlyShowOverride: true },
      defaultCredentials: {},
      defaultRegions: {},
      getDataSource: () => ({ data: [] }),
      ...applicationOverrides,
    };
    const updateStage = jasmine.createSpy('updateStage');
    const updateStageField = jasmine.createSpy('updateStageField');
    const Component = awsResizeAsgStage.component as React.ComponentType<any>;
    const wrapper = mount(
      <Component
        application={application}
        pipeline={{}}
        stage={stage}
        stageFieldUpdated={jasmine.createSpy('stageFieldUpdated')}
        updateStage={updateStage}
        updateStageField={updateStageField}
      />,
    );

    return { stage, updateStage, updateStageField, wrapper };
  }

  it('registers a dedicated stage editor', () => {
    expect(awsResizeAsgStage.component).not.toBe(AmazonStageConfig);
  });

  it('registers a prevent-save numeric validator', () => {
    expect(awsResizeAsgStage.validators).toContain(jasmine.objectContaining({ type: 'custom', preventSave: true }));
  });

  it('prevents saving persisted invalid percentages while allowing valid values and expressions', () => {
    const validator = awsResizeAsgStage.validators.find(({ type }) => type === 'custom');
    const validStage = {
      action: 'scale_up',
      resizeType: 'pct',
      scalePct: 25,
      targetHealthyDeployPercentage: 75,
    };

    expect(validator.validate({}, { ...validStage, scalePct: 12.5 })).toBe(
      'Resize percentage must be a nonnegative integer or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, scalePct: -1 })).toBe(
      'Resize percentage must be a nonnegative integer or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, scalePct: null })).toBe(
      'Resize percentage must be a nonnegative integer or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, targetHealthyDeployPercentage: 75.5 })).toBe(
      'Target healthy percentage must be an integer from 0 through 100 or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, targetHealthyDeployPercentage: 101 })).toBe(
      'Target healthy percentage must be an integer from 0 through 100 or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, scalePct: 125, targetHealthyDeployPercentage: 100 })).toBe('');
    expect(
      validator.validate(
        {},
        {
          ...validStage,
          scalePct: '${ parameters.percentage }',
          targetHealthyDeployPercentage: '${ parameters.healthPercentage }',
        },
      ),
    ).toBe('');
  });

  it('only applies percentage validation to fields used by the selected resize mode', () => {
    const validator = awsResizeAsgStage.validators.find(({ type }) => type === 'custom');

    expect(
      validator.validate(
        {},
        {
          action: 'scale_up',
          resizeType: 'capacity',
          scalePct: 12.5,
          targetHealthyDeployPercentage: 75,
        },
      ),
    ).toBe('');
    expect(
      validator.validate(
        {},
        {
          action: 'scale_down',
          resizeType: 'pct',
          scalePct: 25,
          targetHealthyDeployPercentage: 101,
        },
      ),
    ).toBe('');
  });

  it('prevents saving invalid incremental counts while allowing integers and expressions', () => {
    const validator = awsResizeAsgStage.validators.find(({ type }) => type === 'custom');
    const validStage = { action: 'scale_down', resizeType: 'incr', scaleNum: 2 };
    const message = 'Resize count must be a nonnegative integer or pipeline expression.';

    expect(validator.validate({}, { ...validStage, scaleNum: 2.5 })).toBe(message);
    expect(validator.validate({}, { ...validStage, scaleNum: -1 })).toBe(message);
    expect(validator.validate({}, { ...validStage, scaleNum: Number.NaN })).toBe(message);
    expect(validator.validate({}, { ...validStage, scaleNum: 0 })).toBe('');
    expect(validator.validate({}, { ...validStage, scaleNum: '${ parameters.count }' })).toBe('');
  });

  it('prevents saving invalid exact capacities in min, max, desired order', () => {
    const validator = awsResizeAsgStage.validators.find(({ type }) => type === 'custom');
    const validStage = {
      action: 'scale_exact',
      resizeType: 'exact',
      capacity: { min: 1, max: 2, desired: 3 },
    };

    expect(validator.validate({}, { ...validStage, capacity: { min: 1.5, max: -1, desired: Number.NaN } })).toBe(
      'Minimum capacity must be a nonnegative integer or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, capacity: { min: 1, max: -1, desired: Number.NaN } })).toBe(
      'Maximum capacity must be a nonnegative integer or pipeline expression.',
    );
    expect(validator.validate({}, { ...validStage, capacity: { min: 1, max: 2, desired: Number.NaN } })).toBe(
      'Desired capacity must be a nonnegative integer or pipeline expression.',
    );
    expect(
      validator.validate(
        {},
        {
          ...validStage,
          capacity: {
            min: '${ parameters.min }',
            max: '${ parameters.max }',
            desired: '${ parameters.desired }',
          },
        },
      ),
    ).toBe('');
  });

  it('ignores stale incremental and capacity values outside their active modes', () => {
    const validator = awsResizeAsgStage.validators.find(({ type }) => type === 'custom');

    expect(
      validator.validate(
        {},
        {
          action: 'scale_up',
          resizeType: 'pct',
          scalePct: 25,
          scaleNum: -1,
          capacity: { min: -1, max: -1, desired: -1 },
          targetHealthyDeployPercentage: 75,
        },
      ),
    ).toBe('');
    expect(
      validator.validate(
        {},
        {
          action: 'scale_down',
          resizeType: 'incr',
          scaleNum: 2,
          capacity: { min: -1, max: -1, desired: -1 },
        },
      ),
    ).toBe('');
    expect(
      validator.validate(
        {},
        {
          action: 'scale_exact',
          resizeType: 'exact',
          scaleNum: -1,
          capacity: { min: 1, max: 2, desired: 3 },
        },
      ),
    ).toBe('');
  });

  it('renders AWS target, action, percentage, health threshold, and platform health controls', () => {
    const { wrapper } = renderStage();

    expect(wrapper.find(AccountRegionClusterSelector).exists()).toBe(true);
    expect(wrapper.find(TargetSelect).exists()).toBe(true);
    expect(wrapper.find('select[name="action"] option').map((option) => option.prop('value'))).toEqual([
      'scale_up',
      'scale_down',
      'scale_to_cluster',
      'scale_exact',
    ]);
    expect(wrapper.find('select[name="resizeType"]').exists()).toBe(true);
    expect(wrapper.find('input[name="scalePct"]').prop('value')).toBe(150);
    expect(wrapper.find('input[name="targetHealthyDeployPercentage"]').prop('value')).toBe(100);
    expect(wrapper.find(PlatformHealthOverride).prop('platformHealthType')).toBe('Amazon');
  });

  it('renders exact min, max, and desired capacity without percentage controls', () => {
    const { wrapper } = renderStage({
      action: 'scale_exact',
      capacity: { desired: 4, max: '${ parameters.max }', min: 2 },
      resizeType: 'exact',
    });

    expect(wrapper.find('select[name="resizeType"]').exists()).toBe(false);
    const capacityInputs = wrapper.find('input[data-capacity-field]');
    expect(capacityInputs.map((input) => input.prop('name'))).toEqual([
      'capacity.min',
      'capacity.max',
      'capacity.desired',
    ]);
    expect(capacityInputs.map((input) => input.prop('value'))).toEqual([2, '${ parameters.max }', 4]);
    expect(wrapper.find('input[name="targetHealthyDeployPercentage"]').exists()).toBe(false);
  });

  it('renders an incremental count for scale down without a health threshold', () => {
    const { wrapper } = renderStage({
      action: 'scale_down',
      resizeType: 'incr',
      scaleNum: '${ parameters.count }',
    });

    expect(wrapper.find('input[name="scaleNum"]').map((input) => input.prop('value'))).toEqual([
      '${ parameters.count }',
    ]);
    expect(wrapper.find('input[name="scalePct"]').exists()).toBe(false);
    expect(wrapper.find('input[name="targetHealthyDeployPercentage"]').exists()).toBe(false);
  });

  it('applies AWS defaults to a new stage', () => {
    const { updateStageField } = renderStage(
      {
        action: undefined,
        capacity: undefined,
        cloudProvider: undefined,
        credentials: undefined,
        interestingHealthProviderNames: undefined,
        isNew: true,
        regions: undefined,
        resizeType: undefined,
        target: undefined,
        targetHealthyDeployPercentage: undefined,
      },
      {
        attributes: { platformHealthOnly: true, platformHealthOnlyShowOverride: true },
        defaultCredentials: { aws: 'test-account' },
        defaultRegions: { aws: 'eu-west-1' },
      },
    );

    expect(updateStageField).toHaveBeenCalledWith({
      action: 'scale_up',
      capacity: {},
      cloudProvider: 'aws',
      credentials: 'test-account',
      interestingHealthProviderNames: ['Amazon'],
      regions: ['eu-west-1'],
      resizeType: 'pct',
      target: 'current_asg_dynamic',
      targetHealthyDeployPercentage: 100,
    });
  });

  ['scale_up', 'scale_down', 'scale_to_cluster'].forEach((action) => {
    it(`defaults legacy exact mode to percentage when changing to ${action}`, () => {
      const exact = renderStage({
        action: 'scale_exact',
        capacity: { desired: 4, max: 4, min: 4 },
        resizeType: 'exact',
        scaleNum: 3,
        scalePct: undefined,
      });
      const originalStage = { ...exact.stage, capacity: { ...exact.stage.capacity } };
      exact.updateStage.calls.reset();
      exact.updateStageField.calls.reset();

      exact.wrapper.find('select[name="action"]').simulate('change', { target: { value: action } });

      expect(exact.stage).toEqual(originalStage);
      expect(exact.updateStage).toHaveBeenCalledWith({
        action,
        capacity: {},
        resizeType: 'pct',
        scaleNum: undefined,
        scalePct: 0,
      });
      expect(exact.updateStageField).not.toHaveBeenCalled();
    });
  });

  it('reports stale resize field removals without mutating the stage prop', () => {
    const exact = renderStage({ scaleNum: 3 });
    const originalExactStage = { ...exact.stage, capacity: { ...exact.stage.capacity } };
    exact.updateStage.calls.reset();
    exact.updateStageField.calls.reset();

    exact.wrapper.find('select[name="action"]').simulate('change', { target: { value: 'scale_exact' } });

    expect(exact.stage).toEqual(originalExactStage);
    expect(exact.updateStage).toHaveBeenCalledWith({
      action: 'scale_exact',
      resizeType: 'exact',
      scaleNum: undefined,
      scalePct: undefined,
    });
    expect(exact.updateStageField).not.toHaveBeenCalled();

    const incremental = renderStage({ capacity: { desired: 4 }, scalePct: 25 });
    const originalIncrementalStage = { ...incremental.stage, capacity: { ...incremental.stage.capacity } };
    incremental.updateStage.calls.reset();
    incremental.updateStageField.calls.reset();

    incremental.wrapper.find('select[name="resizeType"]').simulate('change', { target: { value: 'incr' } });

    expect(incremental.stage).toEqual(originalIncrementalStage);
    expect(incremental.updateStage).toHaveBeenCalledWith({
      action: 'scale_up',
      capacity: {},
      resizeType: 'incr',
      scaleNum: 0,
      scalePct: undefined,
    });
    expect(incremental.updateStageField).not.toHaveBeenCalled();

    const percentage = renderStage({ resizeType: 'incr', scaleNum: 3, scalePct: undefined });
    const originalPercentageStage = { ...percentage.stage, capacity: { ...percentage.stage.capacity } };
    percentage.updateStage.calls.reset();
    percentage.updateStageField.calls.reset();

    percentage.wrapper.find('select[name="resizeType"]').simulate('change', { target: { value: 'pct' } });

    expect(percentage.stage).toEqual(originalPercentageStage);
    expect(percentage.updateStage).toHaveBeenCalledWith({
      action: 'scale_up',
      capacity: {},
      resizeType: 'pct',
      scaleNum: undefined,
      scalePct: 0,
    });
    expect(percentage.updateStageField).not.toHaveBeenCalled();
  });

  it('validates numeric modes while preserving pipeline expressions', () => {
    const percentage = renderStage({ scalePct: 125 });
    percentage.updateStageField.calls.reset();
    const percentageInput = percentage.wrapper.find('input[name="scalePct"]');

    expect(percentageInput.prop('aria-invalid')).toBe(false);
    percentageInput.simulate('change', { target: { value: '${ parameters.percentage }' } });
    expect(percentage.updateStageField).toHaveBeenCalledWith({ scalePct: '${ parameters.percentage }' });

    const healthPercentage = renderStage({ targetHealthyDeployPercentage: 100 });
    healthPercentage.updateStageField.calls.reset();
    const healthPercentageInput = healthPercentage.wrapper.find('input[name="targetHealthyDeployPercentage"]');

    expect(healthPercentageInput.prop('aria-invalid')).toBe(false);
    healthPercentageInput.simulate('change', { target: { value: '${ parameters.healthPercentage }' } });
    expect(healthPercentage.updateStageField).toHaveBeenCalledWith({
      targetHealthyDeployPercentage: '${ parameters.healthPercentage }',
    });

    expect(renderStage({ scalePct: -1 }).wrapper.find('input[name="scalePct"]').prop('aria-invalid')).toBe(true);
    expect(renderStage({ scalePct: 12.5 }).wrapper.find('input[name="scalePct"]').prop('aria-invalid')).toBe(true);
    expect(
      renderStage({ targetHealthyDeployPercentage: -1 })
        .wrapper.find('input[name="targetHealthyDeployPercentage"]')
        .prop('aria-invalid'),
    ).toBe(true);
    expect(
      renderStage({ targetHealthyDeployPercentage: 12.5 })
        .wrapper.find('input[name="targetHealthyDeployPercentage"]')
        .prop('aria-invalid'),
    ).toBe(true);
    expect(
      renderStage({ targetHealthyDeployPercentage: 101 })
        .wrapper.find('input[name="targetHealthyDeployPercentage"]')
        .prop('aria-invalid'),
    ).toBe(true);
    expect(
      renderStage({ action: 'scale_down', resizeType: 'incr', scaleNum: 2.5 })
        .wrapper.find('input[name="scaleNum"]')
        .prop('aria-invalid'),
    ).toBe(true);
    expect(
      renderStage({ action: 'scale_exact', capacity: { desired: 3, max: '${ parameters.max }', min: -1 } })
        .wrapper.find('input[data-capacity-field="min"]')
        .prop('aria-invalid'),
    ).toBe(true);
    expect(
      renderStage({ action: 'scale_exact', capacity: { desired: 3, max: '${ parameters.max }', min: 1 } })
        .wrapper.find('input[data-capacity-field="max"]')
        .prop('aria-invalid'),
    ).toBe(false);
  });

  it('keeps invalid resize percentage edits local and only reports valid values', () => {
    const percentage = renderStage({ scalePct: 25 });
    percentage.updateStageField.calls.reset();

    ['12.5', '-1', 'Infinity'].forEach((value) => {
      percentage.wrapper.find('input[name="scalePct"]').simulate('change', { target: { value } });

      const input = percentage.wrapper.find('input[name="scalePct"]');
      expect(input.prop('value')).toBe(value);
      expect(input.prop('aria-invalid')).toBe(true);
      expect(percentage.stage.scalePct).toBe(25);
      expect(percentage.updateStageField).not.toHaveBeenCalled();
    });

    percentage.wrapper.find('input[name="scalePct"]').simulate('change', { target: { value: '125' } });
    expect(percentage.updateStageField).toHaveBeenCalledWith({ scalePct: 125 });

    percentage.updateStageField.calls.reset();
    percentage.wrapper
      .find('input[name="scalePct"]')
      .simulate('change', { target: { value: '${ parameters.percentage }' } });
    expect(percentage.updateStageField).toHaveBeenCalledWith({ scalePct: '${ parameters.percentage }' });
  });

  it('keeps invalid health percentage edits local and only reports valid values', () => {
    const health = renderStage({ targetHealthyDeployPercentage: 75 });
    health.updateStageField.calls.reset();

    ['75.5', '-1', '101', 'Infinity'].forEach((value) => {
      health.wrapper.find('input[name="targetHealthyDeployPercentage"]').simulate('change', { target: { value } });

      const input = health.wrapper.find('input[name="targetHealthyDeployPercentage"]');
      expect(input.prop('value')).toBe(value);
      expect(input.prop('aria-invalid')).toBe(true);
      expect(health.stage.targetHealthyDeployPercentage).toBe(75);
      expect(health.updateStageField).not.toHaveBeenCalled();
    });

    health.wrapper.find('input[name="targetHealthyDeployPercentage"]').simulate('change', { target: { value: '100' } });
    expect(health.updateStageField).toHaveBeenCalledWith({ targetHealthyDeployPercentage: 100 });

    health.updateStageField.calls.reset();
    health.wrapper
      .find('input[name="targetHealthyDeployPercentage"]')
      .simulate('change', { target: { value: '${ parameters.healthPercentage }' } });
    expect(health.updateStageField).toHaveBeenCalledWith({
      targetHealthyDeployPercentage: '${ parameters.healthPercentage }',
    });
  });

  it('keeps invalid incremental edits local and only reports valid values', () => {
    const incremental = renderStage({ action: 'scale_down', resizeType: 'incr', scaleNum: 2 });
    incremental.updateStageField.calls.reset();

    ['2.5', '-1', 'NaN'].forEach((value) => {
      incremental.wrapper.find('input[name="scaleNum"]').simulate('change', { target: { value } });

      const input = incremental.wrapper.find('input[name="scaleNum"]');
      expect(input.prop('value')).toBe(value);
      expect(input.prop('aria-invalid')).toBe(true);
      expect(incremental.stage.scaleNum).toBe(2);
      expect(incremental.updateStageField).not.toHaveBeenCalled();
    });

    incremental.wrapper.find('input[name="scaleNum"]').simulate('change', { target: { value: '3' } });
    expect(incremental.updateStageField).toHaveBeenCalledWith({ scaleNum: 3 });

    incremental.updateStageField.calls.reset();
    incremental.wrapper
      .find('input[name="scaleNum"]')
      .simulate('change', { target: { value: '${ parameters.count }' } });
    expect(incremental.updateStageField).toHaveBeenCalledWith({ scaleNum: '${ parameters.count }' });

    incremental.wrapper.setProps({ stage: { ...incremental.stage, scaleNum: 4 } });
    incremental.wrapper.update();
    expect(incremental.wrapper.find('input[name="scaleNum"]').prop('value')).toBe(4);
  });

  it('keeps invalid exact capacity edits local and reports valid values in capacity order', () => {
    const capacity = { min: 1, max: 2, desired: 3 };

    [
      { field: 'min', value: '1.5' },
      { field: 'max', value: '-1' },
      { field: 'desired', value: 'NaN' },
    ].forEach(({ field, value }) => {
      const exact = renderStage({ action: 'scale_exact', capacity, resizeType: 'exact' });
      exact.updateStageField.calls.reset();

      exact.wrapper.find(`input[data-capacity-field="${field}"]`).simulate('change', { target: { value } });

      const input = exact.wrapper.find(`input[data-capacity-field="${field}"]`);
      expect(input.prop('value')).toBe(value);
      expect(input.prop('aria-invalid')).toBe(true);
      expect(exact.stage.capacity).toEqual(capacity);
      expect(exact.updateStageField).not.toHaveBeenCalled();
    });

    const exact = renderStage({ action: 'scale_exact', capacity, resizeType: 'exact' });
    exact.updateStageField.calls.reset();

    exact.wrapper.find('input[data-capacity-field="min"]').simulate('change', { target: { value: '4' } });
    expect(exact.updateStageField).toHaveBeenCalledWith({ capacity: { min: 4, max: 2, desired: 3 } });

    exact.updateStageField.calls.reset();
    exact.wrapper
      .find('input[data-capacity-field="max"]')
      .simulate('change', { target: { value: '${ parameters.max }' } });
    expect(exact.updateStageField).toHaveBeenCalledWith({
      capacity: { min: 1, max: '${ parameters.max }', desired: 3 },
    });

    exact.updateStageField.calls.reset();
    exact.wrapper.find('input[data-capacity-field="desired"]').simulate('change', { target: { value: '5' } });
    expect(exact.updateStageField).toHaveBeenCalledWith({ capacity: { min: 1, max: 2, desired: 5 } });

    exact.wrapper.setProps({ stage: { ...exact.stage, capacity: { min: 4, max: 5, desired: 6 } } });
    exact.wrapper.update();
    const inputs = exact.wrapper.find('input[data-capacity-field]');
    expect(inputs.map((input) => input.prop('data-capacity-field'))).toEqual(['min', 'max', 'desired']);
    expect(inputs.map((input) => input.prop('value'))).toEqual([4, 5, 6]);
  });

  it('synchronizes controlled percentage inputs when stage props change', () => {
    const rendered = renderStage({ scalePct: 25, targetHealthyDeployPercentage: 75 });

    rendered.wrapper.find('input[name="scalePct"]').simulate('change', { target: { value: '12.5' } });
    rendered.wrapper
      .find('input[name="targetHealthyDeployPercentage"]')
      .simulate('change', { target: { value: '101' } });

    rendered.wrapper.setProps({
      stage: { ...rendered.stage, scalePct: 50, targetHealthyDeployPercentage: 80 },
    });
    rendered.wrapper.update();

    expect(rendered.wrapper.find('input[name="scalePct"]').prop('value')).toBe(50);
    expect(rendered.wrapper.find('input[name="targetHealthyDeployPercentage"]').prop('value')).toBe(80);
  });

  it('resets every controlled draft when the stage refId changes with equal persisted values', () => {
    const validator = awsResizeAsgStage.validators.find(({ type }) => type === 'custom');

    const percentage = renderStage({
      refId: 'stage-a',
      scalePct: 25,
      targetHealthyDeployPercentage: 75,
    });
    percentage.updateStageField.calls.reset();
    percentage.wrapper.find('input[name="scalePct"]').simulate('change', { target: { value: '12.5' } });
    percentage.wrapper
      .find('input[name="targetHealthyDeployPercentage"]')
      .simulate('change', { target: { value: '101' } });
    const nextPercentageStage = { ...percentage.stage, refId: 'stage-b' };
    percentage.wrapper.setProps({ stage: nextPercentageStage });
    percentage.wrapper.update();

    expect(percentage.wrapper.find('input[name="scalePct"]').prop('value')).toBe(25);
    expect(percentage.wrapper.find('input[name="scalePct"]').prop('aria-invalid')).toBe(false);
    expect(percentage.wrapper.find('input[name="targetHealthyDeployPercentage"]').prop('value')).toBe(75);
    expect(percentage.wrapper.find('input[name="targetHealthyDeployPercentage"]').prop('aria-invalid')).toBe(false);
    expect(percentage.updateStageField).not.toHaveBeenCalled();
    expect(validator.validate({}, nextPercentageStage)).toBe('');

    const incremental = renderStage({ action: 'scale_down', refId: 'stage-a', resizeType: 'incr', scaleNum: 2 });
    incremental.updateStageField.calls.reset();
    incremental.wrapper.find('input[name="scaleNum"]').simulate('change', { target: { value: '2.5' } });
    const nextIncrementalStage = { ...incremental.stage, refId: 'stage-b' };
    incremental.wrapper.setProps({ stage: nextIncrementalStage });
    incremental.wrapper.update();

    expect(incremental.wrapper.find('input[name="scaleNum"]').prop('value')).toBe(2);
    expect(incremental.wrapper.find('input[name="scaleNum"]').prop('aria-invalid')).toBe(false);
    expect(incremental.updateStageField).not.toHaveBeenCalled();
    expect(validator.validate({}, nextIncrementalStage)).toBe('');

    const exact = renderStage({
      action: 'scale_exact',
      capacity: { min: 1, max: 2, desired: 3 },
      refId: 'stage-a',
      resizeType: 'exact',
    });
    exact.updateStageField.calls.reset();
    ['min', 'max', 'desired'].forEach((field) => {
      exact.wrapper.find(`input[data-capacity-field="${field}"]`).simulate('change', { target: { value: 'NaN' } });
    });
    const nextExactStage = { ...exact.stage, refId: 'stage-b' };
    exact.wrapper.setProps({ stage: nextExactStage });
    exact.wrapper.update();

    const capacityInputs = exact.wrapper.find('input[data-capacity-field]');
    expect(capacityInputs.map((input) => input.prop('value'))).toEqual([1, 2, 3]);
    expect(capacityInputs.map((input) => input.prop('aria-invalid'))).toEqual([false, false, false]);
    expect(exact.updateStageField).not.toHaveBeenCalled();
    expect(validator.validate({}, nextExactStage)).toBe('');
  });
});
