import { fireEvent, render } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';
import type { FormikProps } from 'formik';
import React from 'react';
import type { Mock, Mocked } from 'vitest';

import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';
import { ServerGroupCapacity, validateGceServerGroupCapacity } from './ServerGroupCapacity';

describe('GCE server group Capacity page', () => {
  it('restores desired capacity and keeps simple capacity min, max, and desired linked', () => {
    const values = command({ autoscalingPolicy: null, capacity: { min: 0, max: 0, desired: 3 } });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(input(wrapper, 'input[aria-label="Desired capacity"]').valueAsNumber).toBe(3);

    fireEvent.change(input(wrapper, 'input[aria-label="Desired capacity"]'), { target: { value: '5' } });

    expect(formik.setFieldValue).toHaveBeenCalledWith('capacity', { min: 5, max: 5, desired: 5 });
  });

  it('defaults commands without an explicit capacity mode to simple capacity', () => {
    const values = command({
      autoscalingPolicy: undefined,
      capacity: { min: 0, max: 0, desired: 3 },
      viewState: { mode: 'create' },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    fireEvent.change(input(wrapper, 'input[aria-label="Desired capacity"]'), { target: { value: '4' } });

    expect(formik.setFieldValue).toHaveBeenCalledWith('capacity', { min: 4, max: 4, desired: 4 });
    expect(validateGceServerGroupCapacity(values)).toEqual({});
  });

  it('derives capacity mode and validation from canonical policy presence instead of stale view state', () => {
    const autoscaling = command({
      capacity: { min: 2, max: 6, desired: 4 },
      autoscalingPolicy: { minNumReplicas: 2, maxNumReplicas: 6 },
      viewState: { mode: 'create', useSimpleCapacity: true },
    });
    const autoscalingProps = testProps(autoscaling);
    const autoscalingWrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={autoscalingProps.formik} />);

    expect(input(autoscalingWrapper, 'input[aria-label="Autoscaling capacity"]')).toBeChecked();
    expect(autoscalingWrapper.queryByLabelText('Minimum capacity')).toBeInTheDocument();
    expect(validateGceServerGroupCapacity(autoscaling)).toEqual({});

    const fixed = command({
      capacity: { min: 3, max: 3, desired: 3 },
      autoscalingPolicy: null,
      viewState: { mode: 'create', useSimpleCapacity: false },
    });
    const fixedProps = testProps(fixed);
    const fixedWrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={fixedProps.formik} />);

    expect(input(fixedWrapper, 'input[aria-label="Simple capacity"]')).toBeChecked();
    expect(fixedWrapper.container.querySelector('input[aria-label="Minimum capacity"]')).not.toBeInTheDocument();
    expect(validateGceServerGroupCapacity(fixed)).toEqual({});
  });

  it('links autoscaling minimum and maximum to the policy and capacity command fields', () => {
    const values = command({
      capacity: { min: 2, max: 6, desired: 4 },
      autoscalingPolicy: { minNumReplicas: 2, maxNumReplicas: 6, unknownPolicyField: 'keep' },
      viewState: { mode: 'clone', useSimpleCapacity: false },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(input(wrapper, 'input[aria-label="Minimum capacity"]').valueAsNumber).toBe(2);
    expect(input(wrapper, 'input[aria-label="Maximum capacity"]').valueAsNumber).toBe(6);
    expect(input(wrapper, 'input[aria-label="Desired capacity"]').valueAsNumber).toBe(4);

    fireEvent.change(input(wrapper, 'input[aria-label="Minimum capacity"]'), { target: { value: '3' } });
    fireEvent.change(input(wrapper, 'input[aria-label="Maximum capacity"]'), { target: { value: '8' } });

    expect(formik.setFieldValue.mock.calls).toEqual([
      ['autoscalingPolicy', { minNumReplicas: 3, maxNumReplicas: 6, unknownPolicyField: 'keep' }],
      ['capacity', { min: 3, max: 6, desired: 4 }],
      ['autoscalingPolicy', { minNumReplicas: 3, maxNumReplicas: 8, unknownPolicyField: 'keep' }],
      ['capacity', { min: 3, max: 8, desired: 4 }],
    ]);
  });

  it('switches an autoscaled clone to fixed capacity without inheriting its ancestor policy', () => {
    const values = command({
      capacity: { min: 1, max: 8, desired: 4 },
      autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 8 },
      enableAutoScaling: true,
      source: { region: 'us-central1', serverGroupName: 'app-v001', useSourceCapacity: true },
      viewState: { mode: 'clone', useSimpleCapacity: false, unrelated: 'keep' },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    fireEvent.click(input(wrapper, 'input[aria-label="Simple capacity"]'));

    expect(values.viewState).toEqual({ mode: 'clone', useSimpleCapacity: true, unrelated: 'keep' });
    expect(values.autoscalingPolicy).toBeNull();
    expect(values.enableAutoScaling).toBe(false);
    expect(values.overwriteAncestorAutoscalingPolicy).toBe(true);
    expect(values.source).toEqual({
      region: 'us-central1',
      serverGroupName: 'app-v001',
      useSourceCapacity: false,
    });
    expect(values.capacity).toEqual({ min: 4, max: 4, desired: 4 });
  });

  (['create', 'createPipeline', 'editPipeline'] as const).forEach((mode) => {
    it(`does not overwrite ancestor autoscaling when switching to fixed capacity in ${mode} mode`, () => {
      const values = command({
        autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 8 },
        overwriteAncestorAutoscalingPolicy: true,
        viewState: { mode, useSimpleCapacity: false },
      });
      const { formik } = testProps(values);
      const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

      fireEvent.click(input(wrapper, 'input[aria-label="Simple capacity"]'));

      expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
    });
  });

  it('does not mark ancestor autoscaling for overwrite when a clone enables autoscaling without an inherited policy', () => {
    const values = command({
      autoscalingPolicy: null,
      overwriteAncestorAutoscalingPolicy: true,
      viewState: { mode: 'clone', useSimpleCapacity: true },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(wrapper.getByLabelText('Simple capacity')).toBeChecked();

    fireEvent.click(wrapper.getByLabelText('Autoscaling capacity'));
    wrapper.rerender(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
    expect(wrapper.getByLabelText('Autoscaling capacity')).toBeChecked();
    expect(wrapper.getByLabelText('Minimum capacity')).toBeVisible();
  });

  it('round trips fixed capacity through autoscaling with a complete synchronized policy', () => {
    const values = command({
      capacity: { min: 3, max: 3, desired: 3 },
      autoscalingPolicy: null,
      enableAutoScaling: false,
      source: { useSourceCapacity: true },
      viewState: { mode: 'clone', useSimpleCapacity: true, unrelated: 'keep' },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    fireEvent.click(input(wrapper, 'input[aria-label="Autoscaling capacity"]'));

    expect(values.autoscalingPolicy).toEqual({
      minNumReplicas: 3,
      maxNumReplicas: 3,
      coolDownPeriodSec: 60,
      cpuUtilization: { utilizationTarget: 0.5 },
    });
    expect(values.capacity).toEqual({ min: 3, max: 3, desired: 3 });
    expect(values.enableAutoScaling).toBe(true);
    expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
    expect(values.source.useSourceCapacity).toBe(false);
    expect(values.viewState).toEqual({ mode: 'clone', useSimpleCapacity: false, unrelated: 'keep' });

    wrapper.rerender(<ServerGroupCapacity app={{} as any} formik={formik} />);
    fireEvent.click(input(wrapper, 'input[aria-label="Simple capacity"]'));

    expect(values.autoscalingPolicy).toBeNull();
    expect(values.capacity).toEqual({ min: 3, max: 3, desired: 3 });
    expect(values.enableAutoScaling).toBe(false);
    expect(values.overwriteAncestorAutoscalingPolicy).toBe(true);
    expect(values.source.useSourceCapacity).toBe(false);
    expect(values.viewState).toEqual({ mode: 'clone', useSimpleCapacity: true, unrelated: 'keep' });

    wrapper.rerender(<ServerGroupCapacity app={{} as any} formik={formik} />);
    fireEvent.click(input(wrapper, 'input[aria-label="Autoscaling capacity"]'));

    expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
  });

  it('does not persist non-finite, negative, fractional, or empty capacities', () => {
    const values = command({
      autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 5 },
      viewState: { mode: 'create', useSimpleCapacity: false },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    ['-1', '1.5', 'Infinity', 'NaN', ''].forEach((value) => {
      fireEvent.change(input(wrapper, 'input[aria-label="Minimum capacity"]'), { target: { value } });
      fireEvent.change(input(wrapper, 'input[aria-label="Maximum capacity"]'), { target: { value } });
      fireEvent.change(input(wrapper, 'input[aria-label="Desired capacity"]'), { target: { value } });
    });

    expect(formik.setFieldValue).not.toHaveBeenCalled();
  });

  it('accepts capacity expressions only in pipeline modes while preserving literal validation', () => {
    const values = command({
      capacity: { min: '${ parameters.min }', max: '${ parameters.max }', desired: '${ parameters.desired }' } as any,
      autoscalingPolicy: {
        minNumReplicas: '${ parameters.min }',
        maxNumReplicas: '${ parameters.max }',
      } as any,
      viewState: { mode: 'editPipeline', useSimpleCapacity: false, templatingEnabled: true },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(validateGceServerGroupCapacity(values)).toEqual({});
    expect(input(wrapper, 'input[aria-label="Minimum capacity"]')).toHaveAttribute('type', 'text');
    expect(input(wrapper, 'input[aria-label="Maximum capacity"]')).toHaveAttribute('type', 'text');
    expect(input(wrapper, 'input[aria-label="Desired capacity"]')).toHaveAttribute('type', 'text');

    fireEvent.change(input(wrapper, 'input[aria-label="Minimum capacity"]'), {
      target: { value: '${ parameters.newMin }' },
    });
    fireEvent.change(input(wrapper, 'input[aria-label="Maximum capacity"]'), {
      target: { value: '${ parameters.newMax }' },
    });
    fireEvent.change(input(wrapper, 'input[aria-label="Desired capacity"]'), {
      target: { value: '${ parameters.newDesired }' },
    });

    expect(formik.setFieldValue.mock.calls).toEqual([
      ['autoscalingPolicy', { minNumReplicas: '${ parameters.newMin }', maxNumReplicas: '${ parameters.max }' }],
      ['capacity', { min: '${ parameters.newMin }', max: '${ parameters.max }', desired: '${ parameters.desired }' }],
      ['autoscalingPolicy', { minNumReplicas: '${ parameters.newMin }', maxNumReplicas: '${ parameters.newMax }' }],
      [
        'capacity',
        { min: '${ parameters.newMin }', max: '${ parameters.newMax }', desired: '${ parameters.desired }' },
      ],
      [
        'capacity',
        { min: '${ parameters.newMin }', max: '${ parameters.newMax }', desired: '${ parameters.newDesired }' },
      ],
    ]);

    expect(
      validateGceServerGroupCapacity(
        command({
          capacity: { desired: '${ parameters.desired }' } as any,
          autoscalingPolicy: {
            minNumReplicas: '${ parameters.min }',
            maxNumReplicas: '${ parameters.max }',
          } as any,
          viewState: { mode: 'create', useSimpleCapacity: false },
        }),
      ),
    ).toEqual({
      capacity: { desired: 'Desired capacity must be a finite non-negative integer.' },
      autoscalingPolicy: {
        minNumReplicas: 'Minimum capacity must be a finite non-negative integer.',
        maxNumReplicas: 'Maximum capacity must be a finite non-negative integer.',
      },
    });
  });

  it('validates integer capacities and enforces min <= desired <= max', () => {
    const { formik } = testProps();
    expect(
      validateGceServerGroupCapacity(
        command({
          capacity: { desired: Number.POSITIVE_INFINITY },
          autoscalingPolicy: { minNumReplicas: -1, maxNumReplicas: 2.5 },
          viewState: { mode: 'create', useSimpleCapacity: false },
        }),
      ),
    ).toEqual({
      capacity: { desired: 'Desired capacity must be a finite non-negative integer.' },
      autoscalingPolicy: {
        minNumReplicas: 'Minimum capacity must be a finite non-negative integer.',
        maxNumReplicas: 'Maximum capacity must be a finite non-negative integer.',
      },
    });

    expect(
      validateGceServerGroupCapacity(
        command({
          capacity: { desired: 2 },
          autoscalingPolicy: { minNumReplicas: 3, maxNumReplicas: 4 },
          viewState: { mode: 'create', useSimpleCapacity: false },
        }),
      ),
    ).toEqual({ capacity: { desired: 'Desired capacity must be at least minimum capacity.' } });

    expect(
      validateGceServerGroupCapacity(
        command({
          capacity: { desired: 5 },
          autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 4 },
          viewState: { mode: 'create', useSimpleCapacity: false },
        }),
      ),
    ).toEqual({ capacity: { desired: 'Desired capacity must not exceed maximum capacity.' } });
  });

  it('requires a zone for zonal commands and invokes zoneChanged with the selected zone', async () => {
    const values = command({ zone: 'persisted-zone' });
    const { adapter, formik, reconciled } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} adapter={adapter} />);

    expect(selectOptions(wrapper, 'Zone')).toEqual([
      ['', 'Select...'],
      ['known-zone-a', 'known-zone-a'],
      ['known-zone-b', 'known-zone-b'],
      ['persisted-zone', 'persisted-zone (unavailable)'],
    ]);
    expect(validateGceServerGroupCapacity(command({ zone: null }))).toEqual({
      zone: 'Zone required.',
    });

    fireEvent.change(wrapper.getByLabelText('Zone'), { target: { value: 'known-zone-b' } });
    await flush();

    const changedCommand = adapter.applyCommandHandler.mock.calls[0][0];
    expect(changedCommand.zone).toBe('known-zone-b');
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'zoneChanged');
    expect(handlerNames(adapter)).toEqual(['zoneChanged', 'selectZonesChanged']);
    expect(formik.setValues).toHaveBeenCalledWith(reconciled);
  });

  it('supports preferred and explicit regional distribution while retaining unavailable selected zones', async () => {
    const values = command({
      regional: true,
      zone: null,
      selectZones: true,
      distributionPolicy: { zones: ['known-zone-a', 'persisted-zone'], targetShape: 'EVEN' },
    });
    const { adapter, formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} adapter={adapter} />);

    expect(wrapper.getByLabelText('Explicit zone distribution')).toBeChecked();
    expect(wrapper.getByLabelText('Preferred zone distribution')).not.toBeChecked();
    expect(wrapper.getByLabelText('Zone known-zone-a')).toBeChecked();
    expect(wrapper.getByLabelText('Zone persisted-zone')).toBeChecked();
    expect(wrapper.getByText(/persisted-zone \(unavailable\)/)).toBeInTheDocument();
    expect(wrapper.getByLabelText('Target shape')).toHaveValue('EVEN');

    fireEvent.click(wrapper.getByLabelText('Preferred zone distribution'));
    await flush();

    const changedCommand = adapter.applyCommandHandler.mock.calls[0][0];
    expect(changedCommand.selectZones).toBe(false);
    expect(changedCommand.distributionPolicy.zones).toEqual(['known-zone-a', 'persisted-zone']);
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'selectZonesChanged');
    expect(handlerNames(adapter)).toEqual(['selectZonesChanged', 'zoneChanged']);
  });

  it('serializes the full regional transition cascade against each reconciled command', async () => {
    const transitions = [deferredCommand(), deferredCommand(), deferredCommand(), deferredCommand()];
    const values = command();
    const { adapter, formik } = transitionProps(values, transitions);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} adapter={adapter} />);

    fireEvent.click(wrapper.getByLabelText('Regional server group'));
    expect(handlerNames(adapter)).toEqual(['regionalChanged']);

    const afterRegional = command({ regional: true, zone: null, transition: 'regional' });
    transitions[0].resolve(update(afterRegional));
    await flush();
    expect(handlerNames(adapter)).toEqual(['regionalChanged', 'regionChanged']);
    expect(adapter.applyCommandHandler.mock.calls[1][0]).toBe(afterRegional);

    const afterRegion = command({ regional: true, zone: null, transition: 'region' });
    transitions[1].resolve(update(afterRegion));
    await flush();
    expect(handlerNames(adapter)).toEqual(['regionalChanged', 'regionChanged', 'zoneChanged']);
    expect(adapter.applyCommandHandler.mock.calls[2][0]).toBe(afterRegion);

    const afterZone = command({ regional: true, zone: null, transition: 'zone' });
    transitions[2].resolve(update(afterZone));
    await flush();
    expect(handlerNames(adapter)).toEqual(['regionalChanged', 'regionChanged', 'zoneChanged', 'selectZonesChanged']);
    expect(adapter.applyCommandHandler.mock.calls[3][0]).toBe(afterZone);
    expect(formik.setValues).not.toHaveBeenCalled();

    const reconciled = command({ regional: true, zone: null, transition: 'selectZones' });
    transitions[3].resolve(update(reconciled));
    await flush();
    expect(formik.setValues).toHaveBeenCalledWith(reconciled);
  });

  it('serializes zonal and explicit-zone reconciliation handlers', async () => {
    const zonalTransitions = [deferredCommand(), deferredCommand()];
    const zonal = transitionProps(command(), zonalTransitions);
    const zonalWrapper = renderPage(
      <ServerGroupCapacity app={{} as any} formik={zonal.formik} adapter={zonal.adapter} />,
    );

    fireEvent.change(zonalWrapper.getByLabelText('Zone'), { target: { value: 'known-zone-b' } });
    expect(handlerNames(zonal.adapter)).toEqual(['zoneChanged']);
    const afterZone = command({ zone: 'known-zone-b', transition: 'zone' });
    zonalTransitions[0].resolve(update(afterZone));
    await flush();
    expect(handlerNames(zonal.adapter)).toEqual(['zoneChanged', 'selectZonesChanged']);
    expect(zonal.adapter.applyCommandHandler.mock.calls[1][0]).toBe(afterZone);
    const zonalReconciled = command({ zone: 'known-zone-b', transition: 'selectZones' });
    zonalTransitions[1].resolve(update(zonalReconciled));
    await flush();
    expect(zonal.formik.setValues).toHaveBeenCalledWith(zonalReconciled);

    const regionalTransitions = [deferredCommand(), deferredCommand()];
    const regional = transitionProps(
      command({ regional: true, zone: null, selectZones: true, distributionPolicy: { zones: ['known-zone-a'] } }),
      regionalTransitions,
    );
    const regionalWrapper = renderPage(
      <ServerGroupCapacity app={{} as any} formik={regional.formik} adapter={regional.adapter} />,
    );

    fireEvent.click(regionalWrapper.getByLabelText('Zone known-zone-b'));
    expect(handlerNames(regional.adapter)).toEqual(['selectZonesChanged']);
    const afterSelectZones = command({
      regional: true,
      zone: null,
      selectZones: true,
      distributionPolicy: { zones: ['known-zone-a', 'known-zone-b'] },
      transition: 'selectZones',
    });
    regionalTransitions[0].resolve(update(afterSelectZones));
    await flush();
    expect(handlerNames(regional.adapter)).toEqual(['selectZonesChanged', 'zoneChanged']);
    expect(regional.adapter.applyCommandHandler.mock.calls[1][0]).toBe(afterSelectZones);
    const regionalReconciled = { ...afterSelectZones, transition: 'zone' };
    regionalTransitions[1].resolve(update(regionalReconciled));
    await flush();
    expect(regional.formik.setValues).toHaveBeenCalledWith(regionalReconciled);
  });

  it('persists target shape and requires zones only for explicit regional distribution', () => {
    const values = command({
      regional: true,
      zone: null,
      distributionPolicy: { zones: [], targetShape: 'EVEN' },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    fireEvent.change(wrapper.getByLabelText('Target shape'), { target: { value: 'ANY' } });

    expect(formik.setFieldValue).toHaveBeenCalledWith('distributionPolicy', { zones: [], targetShape: 'ANY' });
    expect(validateGceServerGroupCapacity(values)).toEqual({});
    expect(validateGceServerGroupCapacity({ ...values, selectZones: true })).toEqual({
      distributionPolicy: { zones: 'At least one zone required.' },
    });
  });

  it('renders the React instance flexibility configurer and immutably persists policy changes', () => {
    const instanceFlexibilityPolicy = {
      instanceSelections: {
        preferred: { rank: 1, machineTypes: ['n2-standard-8'] },
      },
    };
    const values = command({
      regional: true,
      zone: null,
      distributionPolicy: { zones: [], targetShape: 'BALANCED' },
      instanceFlexibilityPolicy,
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(wrapper.getByRole('textbox', { name: 'Selection name' })).toHaveValue('preferred');
    expect(wrapper.getByRole('spinbutton', { name: 'Rank (optional)' })).toHaveValue(1);
    expect(wrapper.getByRole('textbox', { name: 'Machine type 1 for selection preferred' })).toHaveValue(
      'n2-standard-8',
    );
    // Regional BALANCED commands satisfy the configurer's regional and target shape requirements.
    expect(wrapper.queryByText('Flexibility requires a regional server group.')).not.toBeInTheDocument();
    expect(wrapper.queryByText(/Flexibility requires target shape/)).not.toBeInTheDocument();

    fireEvent.change(wrapper.getByRole('textbox', { name: 'Machine type 1 for selection preferred' }), {
      target: { value: 'e2-standard-8' },
    });

    expect(formik.setFieldValue).toHaveBeenCalledWith('instanceFlexibilityPolicy', {
      instanceSelections: {
        preferred: { rank: 1, machineTypes: ['e2-standard-8'] },
      },
    });
    expect(instanceFlexibilityPolicy).toEqual({
      instanceSelections: {
        preferred: { rank: 1, machineTypes: ['n2-standard-8'] },
      },
    });
  });

  it('passes the Formik flexibility validation error to the configurer', () => {
    const values = command({
      regional: true,
      zone: null,
      distributionPolicy: { zones: [], targetShape: 'BALANCED' },
      instanceFlexibilityPolicy: {
        instanceSelections: {
          preferred: { rank: -1, machineTypes: [''] },
        },
      },
    });
    const { formik } = testProps(values);
    (formik as any).errors = {
      instanceFlexibilityPolicy: 'Instance flexibility policy is invalid.',
    };
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(wrapper.getByRole('alert')).toHaveTextContent('Instance flexibility policy is invalid.');
    expect(wrapper.getByRole('spinbutton', { name: 'Rank (optional)' })).toHaveAccessibleDescription(
      'Instance flexibility policy is invalid.',
    );
  });

  it('blocks invalid enabled flexibility while allowing absent and explicitly empty policies', () => {
    const enabledPolicy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    };

    expect(
      validateGceServerGroupCapacity(
        command({
          regional: false,
          distributionPolicy: { zones: [], targetShape: 'BALANCED' },
          instanceFlexibilityPolicy: enabledPolicy,
        }),
      ).instanceFlexibilityPolicy,
    ).toBeDefined();
    expect(
      validateGceServerGroupCapacity(
        command({
          regional: true,
          zone: null,
          distributionPolicy: { zones: [], targetShape: 'EVEN' },
          instanceFlexibilityPolicy: enabledPolicy,
        }),
      ).instanceFlexibilityPolicy,
    ).toBeDefined();

    ['BALANCED', 'ANY', 'ANY_SINGLE_ZONE'].forEach((targetShape) => {
      expect(
        validateGceServerGroupCapacity(
          command({
            regional: true,
            zone: null,
            distributionPolicy: { zones: [], targetShape },
            instanceFlexibilityPolicy: enabledPolicy,
          }),
        ),
      ).toEqual({});
    });

    expect(validateGceServerGroupCapacity(command({ instanceFlexibilityPolicy: undefined }))).toEqual({});
    expect(
      validateGceServerGroupCapacity(
        command({
          instanceFlexibilityPolicy: { instanceSelections: {} },
        }),
      ),
    ).toEqual({});
  });

  it('offers every target shape supported by the merged Google contract', () => {
    const values = command({
      regional: true,
      zone: null,
      backingData: {
        filtered: { zones: ['known-zone-a', 'known-zone-b'] },
      },
    });
    const { formik } = testProps(values);
    const wrapper = renderPage(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(selectOptions(wrapper, 'Target shape')).toEqual([
      ['', 'Select...'],
      ['ANY', 'ANY'],
      ['EVEN', 'EVEN'],
      ['BALANCED', 'BALANCED'],
      ['ANY_SINGLE_ZONE', 'ANY_SINGLE_ZONE'],
    ]);
  });
});

function selectOptions(wrapper: RenderResult, label: string): string[][] {
  return Array.from(wrapper.getByLabelText(label).querySelectorAll('option')).map((option) => [
    option.value,
    option.textContent || '',
  ]);
}

function input(wrapper: RenderResult, selector: string): HTMLInputElement {
  const element = wrapper.container.querySelector<HTMLInputElement>(selector);
  if (!element) {
    throw new Error(`Missing input: ${selector}`);
  }
  return element;
}

function testProps(values = command()) {
  const formik = ({
    values,
    setFieldValue: vi.fn().mockImplementation((field: string, value: any) => {
      values[field] = value;
    }),
    setValues: vi.fn(),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const reconciled = command({ region: 'reconciled-region' });
  const adapter = ({
    applyCommandHandler: vi.fn().mockResolvedValue({ command: reconciled, result: { dirty: {} } }),
  } as unknown) as Mocked<IGceServerGroupWizardAdapter>;
  return { adapter, formik, reconciled };
}

function transitionProps(values: IGceServerGroupCommand, transitions: Array<ReturnType<typeof deferredCommand>>) {
  const formik = ({
    values,
    setFieldValue: vi.fn(),
    setValues: vi.fn(),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyCommandHandler: vi
      .fn()
      .mockImplementation(() => transitions[(adapter.applyCommandHandler as Mock).mock.calls.length - 1].promise),
  } as unknown) as Mocked<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function handlerNames(adapter: Mocked<IGceServerGroupWizardAdapter>): string[] {
  return adapter.applyCommandHandler.mock.calls.map((args) => args[1]);
}

function update(commandValue: IGceServerGroupCommand) {
  return { command: commandValue, result: { dirty: {} } };
}

function deferredCommand() {
  let resolve!: (value: ReturnType<typeof update>) => void;
  const promise = new Promise<ReturnType<typeof update>>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account',
    regional: false,
    region: 'us-central1',
    zone: 'known-zone-a',
    capacity: { min: 2, max: 6, desired: 4 },
    autoscalingPolicy: { minNumReplicas: 2, maxNumReplicas: 6 },
    distributionPolicy: { zones: [], targetShape: 'EVEN' },
    selectZones: false,
    backingData: {
      distributionPolicyTargetShapes: ['ANY', 'EVEN'],
      filtered: { zones: ['known-zone-a', 'known-zone-b'] },
    },
    viewState: { mode: 'create', useSimpleCapacity: true },
    ...overrides,
  } as IGceServerGroupCommand;
}

async function flush(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

function renderPage(component: React.ReactElement): RenderResult {
  const container = document.body.appendChild(document.createElement('div'));
  return render(component, { baseElement: container, container });
}
