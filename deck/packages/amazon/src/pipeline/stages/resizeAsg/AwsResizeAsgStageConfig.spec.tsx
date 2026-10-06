import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';
import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { awsResizeAsgStage } from './awsResizeAsgStage';

describe('AWS Resize Server Group stage', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue([]);
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([]);
  });

  function renderStage(
    stageOverrides: Record<string, any> = {},
    applicationOverrides: Record<string, any> = {},
    pipelineOverrides: Record<string, any> = {},
  ) {
    const initialStage = {
      action: 'scale_up',
      capacity: {},
      cloudProvider: 'aws',
      regions: [],
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
    const updateStage = vi.fn();
    const updateStageField = vi.fn();
    const Component = awsResizeAsgStage.component as React.ComponentType<any>;
    let replaceStage: React.Dispatch<React.SetStateAction<any>>;

    function StageHarness() {
      const [stage, setStage] = React.useState(initialStage);
      replaceStage = setStage;
      const update = (changes: any) => {
        updateStage(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      const updateField = (changes: any) => {
        updateStageField(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      return (
        <Component
          application={application}
          pipeline={{ strategy: true, ...pipelineOverrides }}
          stage={stage}
          stageFieldUpdated={vi.fn()}
          updateStage={update}
          updateStageField={updateField}
        />
      );
    }

    return {
      initialStage,
      replaceStage: (stage: any) => act(() => replaceStage(stage)),
      updateStage,
      updateStageField,
      ...render(<StageHarness />),
    };
  }

  const validator = () => awsResizeAsgStage.validators.find(({ type }) => type === 'custom');

  it('registers a dedicated stage editor and prevent-save numeric validator', () => {
    expect(awsResizeAsgStage.component).not.toBe(AmazonStageConfig);
    expect(validator()).toEqual(expect.objectContaining({ type: 'custom', preventSave: true }));
  });

  it('validates percentage and health values while allowing integers and expressions', () => {
    const valid = { action: 'scale_up', resizeType: 'pct', scalePct: 25, targetHealthyDeployPercentage: 75 };
    expect(validator().validate({}, { ...valid, scalePct: 12.5 })).toBe(
      'Resize percentage must be a nonnegative integer or pipeline expression.',
    );
    expect(validator().validate({}, { ...valid, scalePct: -1 })).toBe(
      'Resize percentage must be a nonnegative integer or pipeline expression.',
    );
    expect(validator().validate({}, { ...valid, targetHealthyDeployPercentage: 101 })).toBe(
      'Target healthy percentage must be an integer from 0 through 100 or pipeline expression.',
    );
    expect(validator().validate({}, { ...valid, scalePct: 125, targetHealthyDeployPercentage: 100 })).toBe('');
    expect(
      validator().validate(
        {},
        {
          ...valid,
          scalePct: '${ parameters.percentage }',
          targetHealthyDeployPercentage: '${ parameters.healthPercentage }',
        },
      ),
    ).toBe('');
  });

  it('validates only fields used by incremental and exact modes', () => {
    const countMessage = 'Resize count must be a nonnegative integer or pipeline expression.';
    expect(validator().validate({}, { action: 'scale_down', resizeType: 'incr', scaleNum: 2.5 })).toBe(countMessage);
    expect(validator().validate({}, { action: 'scale_down', resizeType: 'incr', scaleNum: 0 })).toBe('');
    expect(
      validator().validate(
        {},
        { action: 'scale_exact', resizeType: 'exact', capacity: { min: 1.5, max: -1, desired: Number.NaN } },
      ),
    ).toBe('Minimum capacity must be a nonnegative integer or pipeline expression.');
    expect(
      validator().validate(
        {},
        { action: 'scale_exact', resizeType: 'exact', capacity: { min: 1, max: -1, desired: Number.NaN } },
      ),
    ).toBe('Maximum capacity must be a nonnegative integer or pipeline expression.');
    expect(
      validator().validate(
        {},
        { action: 'scale_exact', resizeType: 'exact', capacity: { min: 1, max: 2, desired: Number.NaN } },
      ),
    ).toBe('Desired capacity must be a nonnegative integer or pipeline expression.');
    expect(
      validator().validate(
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
  });

  it('rejects a null percentage in percentage mode', () => {
    expect(
      validator().validate(
        {},
        { action: 'scale_up', resizeType: 'pct', scalePct: null, targetHealthyDeployPercentage: 100 },
      ),
    ).toBe('Resize percentage must be a nonnegative integer or pipeline expression.');
  });

  it('rejects a fractional target health percentage', () => {
    expect(
      validator().validate(
        {},
        { action: 'scale_up', resizeType: 'pct', scalePct: 25, targetHealthyDeployPercentage: 99.5 },
      ),
    ).toBe('Target healthy percentage must be an integer from 0 through 100 or pipeline expression.');
  });

  it('rejects negative and non-finite incremental counts while accepting an expression', () => {
    const message = 'Resize count must be a nonnegative integer or pipeline expression.';

    expect(validator().validate({}, { action: 'scale_down', resizeType: 'incr', scaleNum: -1 })).toBe(message);
    expect(validator().validate({}, { action: 'scale_down', resizeType: 'incr', scaleNum: Number.NaN })).toBe(message);
    expect(validator().validate({}, { action: 'scale_down', resizeType: 'incr', scaleNum: '${ count }' })).toBe('');
  });

  it('accepts expressions for every exact capacity', () => {
    expect(
      validator().validate(
        {},
        {
          action: 'scale_exact',
          resizeType: 'exact',
          capacity: { min: '${ min }', max: '${ max }', desired: '${ desired }' },
        },
      ),
    ).toBe('');
  });

  it('validates only eligible fields across incremental, exact, and scale-down percentage modes', () => {
    expect(
      validator().validate(
        {},
        {
          action: 'scale_up',
          resizeType: 'incr',
          scaleNum: 1,
          scalePct: null,
          capacity: { min: -1, max: -1, desired: -1 },
          targetHealthyDeployPercentage: 100,
        },
      ),
    ).toBe('');
    expect(
      validator().validate(
        {},
        {
          action: 'scale_exact',
          resizeType: 'exact',
          scaleNum: -1,
          scalePct: null,
          capacity: { min: 1, max: 2, desired: 2 },
          targetHealthyDeployPercentage: 99.5,
        },
      ),
    ).toBe('');
    expect(
      validator().validate(
        {},
        {
          action: 'scale_down',
          resizeType: 'pct',
          scaleNum: Number.NaN,
          scalePct: 25,
          capacity: { min: -1, max: -1, desired: -1 },
          targetHealthyDeployPercentage: 99.5,
        },
      ),
    ).toBe('');
  });

  it('renders target, action, percentage, health threshold, and task completion controls', () => {
    renderStage();

    expect(within(getFormGroupByLabel('Target')).getByRole('combobox')).toHaveAttribute(
      'placeholder',
      '(Deprecated) Current Server Group',
    );
    const action = within(getFormGroupByLabel('Action')).getByRole('combobox');
    expect(
      within(action)
        .getAllByRole('option')
        .map((option) => (option as HTMLOptionElement).value),
    ).toEqual(['scale_up', 'scale_down', 'scale_to_cluster', 'scale_exact']);
    expect(screen.getByRole('textbox', { name: 'Resize percentage' })).toHaveValue('150');
    expect(screen.getByRole('textbox', { name: 'Target healthy percentage' })).toHaveValue('100');
    expect(getFormGroupByLabel('Task Completion')).toHaveTextContent('Amazon');
  });

  it('renders exact min, max, and desired capacity without percentage controls', () => {
    renderStage({
      action: 'scale_exact',
      capacity: { desired: 4, max: '${ parameters.max }', min: 2 },
      resizeType: 'exact',
    });

    expect(screen.getByRole('textbox', { name: 'Minimum capacity' })).toHaveValue('2');
    expect(screen.getByRole('textbox', { name: 'Maximum capacity' })).toHaveValue('${ parameters.max }');
    expect(screen.getByRole('textbox', { name: 'Desired capacity' })).toHaveValue('4');
    expect(screen.queryByRole('textbox', { name: 'Resize percentage' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Target healthy percentage' })).not.toBeInTheDocument();
  });

  it('renders an incremental count for scale down without a health threshold', () => {
    renderStage({ action: 'scale_down', resizeType: 'incr', scaleNum: '${ parameters.count }' });

    expect(screen.getByRole('textbox', { name: 'Resize count' })).toHaveValue('${ parameters.count }');
    expect(screen.queryByRole('textbox', { name: 'Target healthy percentage' })).not.toBeInTheDocument();
  });

  it('applies AWS defaults to a new stage', () => {
    const rendered = renderStage(
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

    expect(rendered.updateStageField).toHaveBeenCalledWith({
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

  describe('mode transitions', () => {
    it.each(['scale_up', 'scale_down', 'scale_to_cluster'])(
      'defaults legacy exact mode to percentage when changing to %s',
      (action) => {
        const rendered = renderStage({
          action: 'scale_exact',
          capacity: { desired: 4, max: 4, min: 4 },
          resizeType: 'exact',
          scaleNum: 3,
          scalePct: undefined,
        });
        const original = { ...rendered.initialStage, capacity: { ...rendered.initialStage.capacity } };
        rendered.updateStage.mockClear();
        rendered.updateStageField.mockClear();

        fireEvent.change(within(getFormGroupByLabel('Action')).getByRole('combobox'), { target: { value: action } });

        expect(rendered.initialStage).toEqual(original);
        expect(rendered.updateStage.mock.lastCall?.[0]).toStrictEqual({
          action,
          capacity: {},
          resizeType: 'pct',
          scaleNum: undefined,
          scalePct: 0,
        });
        expect(rendered.updateStageField).not.toHaveBeenCalled();
      },
    );

    it('clears stale fields when changing between exact, incremental, and percentage modes', () => {
      const exact = renderStage({ scaleNum: 3 });
      const originalExact = { ...exact.initialStage, capacity: { ...exact.initialStage.capacity } };
      exact.updateStage.mockClear();
      exact.updateStageField.mockClear();

      fireEvent.change(within(getFormGroupByLabel('Action')).getByRole('combobox'), {
        target: { value: 'scale_exact' },
      });

      expect(exact.initialStage).toEqual(originalExact);
      expect(exact.updateStage.mock.lastCall?.[0]).toStrictEqual({
        action: 'scale_exact',
        resizeType: 'exact',
        scaleNum: undefined,
        scalePct: undefined,
      });
      expect(exact.updateStageField).not.toHaveBeenCalled();

      exact.updateStage.mockClear();
      fireEvent.change(within(getFormGroupByLabel('Action')).getByRole('combobox'), {
        target: { value: 'scale_up' },
      });
      fireEvent.change(within(getFormGroupByLabel('Type')).getByRole('combobox'), {
        target: { value: 'incr' },
      });
      expect(exact.updateStage.mock.lastCall?.[0]).toStrictEqual({
        action: 'scale_up',
        capacity: {},
        resizeType: 'incr',
        scaleNum: 0,
        scalePct: undefined,
      });

      fireEvent.change(within(getFormGroupByLabel('Type')).getByRole('combobox'), {
        target: { value: 'pct' },
      });
      expect(exact.updateStage.mock.lastCall?.[0]).toStrictEqual({
        action: 'scale_up',
        capacity: {},
        resizeType: 'pct',
        scaleNum: undefined,
        scalePct: 0,
      });
    });
  });

  it('keeps invalid percentage edits local and reports valid values and expressions', () => {
    const rendered = renderStage({ scalePct: 25 });
    rendered.updateStageField.mockClear();
    const percentage = screen.getByRole('textbox', { name: 'Resize percentage' });

    for (const value of ['12.5', '-1', 'Infinity']) {
      fireEvent.change(percentage, { target: { value } });
      expect(percentage).toHaveValue(value);
      expect(percentage).toHaveAttribute('aria-invalid', 'true');
      expect(rendered.initialStage.scalePct).toBe(25);
      expect(rendered.updateStageField).not.toHaveBeenCalled();
    }

    fireEvent.change(percentage, { target: { value: '125' } });
    expect(rendered.updateStageField).toHaveBeenCalledWith({ scalePct: 125 });
    rendered.updateStageField.mockClear();
    fireEvent.change(percentage, { target: { value: '${ parameters.percentage }' } });
    expect(rendered.updateStageField).toHaveBeenCalledWith({ scalePct: '${ parameters.percentage }' });
  });

  it('keeps invalid health and incremental edits local and reports valid values', () => {
    const health = renderStage({ targetHealthyDeployPercentage: 75 });
    health.updateStageField.mockClear();
    const healthPercentage = screen.getByRole('textbox', { name: 'Target healthy percentage' });

    for (const value of ['75.5', '-1', '101', 'Infinity']) {
      fireEvent.change(healthPercentage, { target: { value } });
      expect(healthPercentage).toHaveValue(value);
      expect(healthPercentage).toHaveAttribute('aria-invalid', 'true');
      expect(health.updateStageField).not.toHaveBeenCalled();
    }
    fireEvent.change(healthPercentage, { target: { value: '100' } });
    expect(health.updateStageField).toHaveBeenCalledWith({ targetHealthyDeployPercentage: 100 });
    health.unmount();

    const incremental = renderStage({ action: 'scale_down', resizeType: 'incr', scaleNum: 2 });
    incremental.updateStageField.mockClear();
    const count = screen.getByRole('textbox', { name: 'Resize count' });
    for (const value of ['2.5', '-1', 'NaN']) {
      fireEvent.change(count, { target: { value } });
      expect(count).toHaveValue(value);
      expect(count).toHaveAttribute('aria-invalid', 'true');
      expect(incremental.updateStageField).not.toHaveBeenCalled();
    }
    fireEvent.change(count, { target: { value: '3' } });
    expect(incremental.updateStageField).toHaveBeenCalledWith({ scaleNum: 3 });
  });

  it('keeps invalid exact capacity edits local', () => {
    const rendered = renderStage({
      action: 'scale_exact',
      capacity: { min: 1, max: 2, desired: 3 },
      resizeType: 'exact',
    });
    rendered.updateStageField.mockClear();

    for (const [name, value] of [
      ['Minimum capacity', '1.5'],
      ['Maximum capacity', '-1'],
      ['Desired capacity', 'NaN'],
    ]) {
      const input = screen.getByRole('textbox', { name });
      fireEvent.change(input, { target: { value } });
      expect(input).toHaveValue(value);
      expect(input).toHaveAttribute('aria-invalid', 'true');
    }
    expect(rendered.initialStage.capacity).toEqual({ min: 1, max: 2, desired: 3 });
    expect(rendered.updateStageField).not.toHaveBeenCalled();
  });

  it('reports valid exact capacities in min, max, desired order', () => {
    const rendered = renderStage({
      action: 'scale_exact',
      capacity: { min: 1, max: 2, desired: 3 },
      resizeType: 'exact',
    });
    rendered.updateStageField.mockClear();
    const input = (name: string) => screen.getByRole('textbox', { name });

    fireEvent.change(input('Minimum capacity'), { target: { value: '4' } });
    expect(rendered.updateStageField).toHaveBeenLastCalledWith({ capacity: { min: 4, max: 2, desired: 3 } });
    fireEvent.change(input('Maximum capacity'), { target: { value: '${ parameters.max }' } });
    expect(rendered.updateStageField).toHaveBeenLastCalledWith({
      capacity: { min: 4, max: '${ parameters.max }', desired: 3 },
    });
    fireEvent.change(input('Desired capacity'), { target: { value: '5' } });
    expect(rendered.updateStageField).toHaveBeenLastCalledWith({
      capacity: { min: 4, max: '${ parameters.max }', desired: 5 },
    });
  });

  it('synchronizes controlled drafts when persisted stage values change', () => {
    const rendered = renderStage({ refId: 'stage-a', scalePct: 25, targetHealthyDeployPercentage: 75 });
    fireEvent.change(screen.getByRole('textbox', { name: 'Resize percentage' }), { target: { value: '12.5' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Target healthy percentage' }), {
      target: { value: '101' },
    });

    rendered.replaceStage({ ...rendered.initialStage, scalePct: 50, targetHealthyDeployPercentage: 80 });

    expect(screen.getByRole('textbox', { name: 'Resize percentage' })).toHaveValue('50');
    expect(screen.getByRole('textbox', { name: 'Target healthy percentage' })).toHaveValue('80');
  });

  it('resets every controlled draft when refId changes with equal persisted values', () => {
    const percentage = renderStage({ refId: 'stage-a', scalePct: 25, targetHealthyDeployPercentage: 75 });
    fireEvent.change(screen.getByRole('textbox', { name: 'Resize percentage' }), { target: { value: '12.5' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Target healthy percentage' }), {
      target: { value: '101' },
    });
    percentage.updateStageField.mockClear();
    percentage.replaceStage({ ...percentage.initialStage, refId: 'stage-b' });
    expect(screen.getByRole('textbox', { name: 'Resize percentage' })).toHaveValue('25');
    expect(screen.getByRole('textbox', { name: 'Target healthy percentage' })).toHaveValue('75');
    expect(percentage.updateStageField).not.toHaveBeenCalled();
    percentage.unmount();

    const incremental = renderStage({ action: 'scale_down', refId: 'stage-a', resizeType: 'incr', scaleNum: 2 });
    fireEvent.change(screen.getByRole('textbox', { name: 'Resize count' }), { target: { value: '2.5' } });
    incremental.updateStageField.mockClear();
    incremental.replaceStage({ ...incremental.initialStage, refId: 'stage-b' });
    expect(screen.getByRole('textbox', { name: 'Resize count' })).toHaveValue('2');
    expect(incremental.updateStageField).not.toHaveBeenCalled();
    incremental.unmount();

    const exact = renderStage({
      action: 'scale_exact',
      capacity: { min: 1, max: 2, desired: 3 },
      refId: 'stage-a',
      resizeType: 'exact',
    });
    for (const name of ['Minimum capacity', 'Maximum capacity', 'Desired capacity']) {
      fireEvent.change(screen.getByRole('textbox', { name }), { target: { value: 'NaN' } });
    }
    exact.updateStageField.mockClear();
    exact.replaceStage({ ...exact.initialStage, refId: 'stage-b' });
    expect(screen.getByRole('textbox', { name: 'Minimum capacity' })).toHaveValue('1');
    expect(screen.getByRole('textbox', { name: 'Maximum capacity' })).toHaveValue('2');
    expect(screen.getByRole('textbox', { name: 'Desired capacity' })).toHaveValue('3');
    expect(exact.updateStageField).not.toHaveBeenCalled();
  });

  it('renders the account, region, and cluster selector outside strategy pipelines', async () => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([{ name: 'test-account' }] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue(['eu-west-1'] as any);
    renderStage(
      { cloudProviderType: 'aws', credentials: 'test-account', regions: ['eu-west-1'] },
      {},
      { strategy: false },
    );

    await waitFor(() =>
      expect(within(getFormGroupByLabel('Account')).getByRole('combobox')).toHaveValue('test-account'),
    );
    expect(within(getFormGroupByLabel('Regions')).getByRole('checkbox', { name: 'eu-west-1' })).toBeChecked();
    expect(within(getFormGroupByLabel('Cluster')).getByRole('combobox')).toHaveDisplayValue('Select a cluster...');
  });
});
