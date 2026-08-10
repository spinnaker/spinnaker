import type { FormikProps } from 'formik';
import { shallow } from 'enzyme';
import React from 'react';

import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';
import { ServerGroupCapacity } from './ServerGroupCapacity';

describe('GCE server group Capacity page', () => {
  it('restores desired capacity and keeps simple capacity min, max, and desired linked', () => {
    const values = command({ autoscalingPolicy: null, capacity: { min: 0, max: 0, desired: 3 } });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(wrapper.find('input[aria-label="Desired capacity"]').prop('value')).toBe(3);

    wrapper.find('input[aria-label="Desired capacity"]').simulate('change', { target: { value: '5' } });

    expect(formik.setFieldValue).toHaveBeenCalledWith('capacity', { min: 5, max: 5, desired: 5 });
  });

  it('defaults commands without an explicit capacity mode to simple capacity', () => {
    const values = command({
      autoscalingPolicy: undefined,
      capacity: { min: 0, max: 0, desired: 3 },
      viewState: { mode: 'create' },
    });
    const { formik } = testProps(values);
    const page = new ServerGroupCapacity({ app: {} as any, formik } as any);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    wrapper.find('input[aria-label="Desired capacity"]').simulate('change', { target: { value: '4' } });

    expect(formik.setFieldValue).toHaveBeenCalledWith('capacity', { min: 4, max: 4, desired: 4 });
    expect(page.validate(values)).toEqual({});
  });

  it('derives capacity mode and validation from canonical policy presence instead of stale view state', () => {
    const autoscaling = command({
      capacity: { min: 2, max: 6, desired: 4 },
      autoscalingPolicy: { minNumReplicas: 2, maxNumReplicas: 6 },
      viewState: { mode: 'create', useSimpleCapacity: true },
    });
    const autoscalingProps = testProps(autoscaling);
    const autoscalingWrapper = shallow(<ServerGroupCapacity app={{} as any} formik={autoscalingProps.formik} />);

    expect(autoscalingWrapper.find('input[aria-label="Autoscaling capacity"]').prop('checked')).toBe(true);
    expect(autoscalingWrapper.find('input[aria-label="Minimum capacity"]').exists()).toBe(true);
    expect(
      new ServerGroupCapacity({ app: {} as any, formik: autoscalingProps.formik } as any).validate(autoscaling),
    ).toEqual({});

    const fixed = command({
      capacity: { min: 3, max: 3, desired: 3 },
      autoscalingPolicy: null,
      viewState: { mode: 'create', useSimpleCapacity: false },
    });
    const fixedProps = testProps(fixed);
    const fixedWrapper = shallow(<ServerGroupCapacity app={{} as any} formik={fixedProps.formik} />);

    expect(fixedWrapper.find('input[aria-label="Simple capacity"]').prop('checked')).toBe(true);
    expect(fixedWrapper.find('input[aria-label="Minimum capacity"]').exists()).toBe(false);
    expect(new ServerGroupCapacity({ app: {} as any, formik: fixedProps.formik } as any).validate(fixed)).toEqual({});
  });

  it('links autoscaling minimum and maximum to the policy and capacity command fields', () => {
    const values = command({
      capacity: { min: 2, max: 6, desired: 4 },
      autoscalingPolicy: { minNumReplicas: 2, maxNumReplicas: 6, unknownPolicyField: 'keep' },
      viewState: { mode: 'clone', useSimpleCapacity: false },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(wrapper.find('input[aria-label="Minimum capacity"]').prop('value')).toBe(2);
    expect(wrapper.find('input[aria-label="Maximum capacity"]').prop('value')).toBe(6);
    expect(wrapper.find('input[aria-label="Desired capacity"]').prop('value')).toBe(4);

    wrapper.find('input[aria-label="Minimum capacity"]').simulate('change', { target: { value: '3' } });
    wrapper.find('input[aria-label="Maximum capacity"]').simulate('change', { target: { value: '8' } });

    expect(formik.setFieldValue.calls.allArgs()).toEqual([
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
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    wrapper.find('input[aria-label="Simple capacity"]').simulate('change', { target: { checked: true } });

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
      const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

      wrapper.find('input[aria-label="Simple capacity"]').simulate('change', { target: { checked: true } });

      expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
    });
  });

  it('does not overwrite ancestor autoscaling for a clone without an autoscaling policy', () => {
    const values = command({
      autoscalingPolicy: null,
      overwriteAncestorAutoscalingPolicy: true,
      viewState: { mode: 'clone', useSimpleCapacity: true },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    wrapper.find('input[aria-label="Simple capacity"]').simulate('change', { target: { checked: true } });

    expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
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
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    wrapper.find('input[aria-label="Autoscaling capacity"]').simulate('change', { target: { checked: true } });

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

    wrapper.setProps({ formik });
    wrapper.find('input[aria-label="Simple capacity"]').simulate('change', { target: { checked: true } });

    expect(values.autoscalingPolicy).toBeNull();
    expect(values.capacity).toEqual({ min: 3, max: 3, desired: 3 });
    expect(values.enableAutoScaling).toBe(false);
    expect(values.overwriteAncestorAutoscalingPolicy).toBe(true);
    expect(values.source.useSourceCapacity).toBe(false);
    expect(values.viewState).toEqual({ mode: 'clone', useSimpleCapacity: true, unrelated: 'keep' });

    wrapper.setProps({ formik });
    wrapper.find('input[aria-label="Autoscaling capacity"]').simulate('change', { target: { checked: true } });

    expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
  });

  it('does not persist non-finite, negative, fractional, or empty capacities', () => {
    const values = command({
      autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 5 },
      viewState: { mode: 'create', useSimpleCapacity: false },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    ['-1', '1.5', 'Infinity', 'NaN', ''].forEach((value) => {
      wrapper.find('input[aria-label="Minimum capacity"]').simulate('change', { target: { value } });
      wrapper.find('input[aria-label="Maximum capacity"]').simulate('change', { target: { value } });
      wrapper.find('input[aria-label="Desired capacity"]').simulate('change', { target: { value } });
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
    const page = new ServerGroupCapacity({ app: {} as any, formik } as any);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    expect(page.validate(values)).toEqual({});
    expect(wrapper.find('input[aria-label="Minimum capacity"]').prop('type')).toBe('text');
    expect(wrapper.find('input[aria-label="Maximum capacity"]').prop('type')).toBe('text');
    expect(wrapper.find('input[aria-label="Desired capacity"]').prop('type')).toBe('text');

    wrapper.find('input[aria-label="Minimum capacity"]').simulate('change', {
      target: { value: '${ parameters.newMin }' },
    });
    wrapper.find('input[aria-label="Maximum capacity"]').simulate('change', {
      target: { value: '${ parameters.newMax }' },
    });
    wrapper.find('input[aria-label="Desired capacity"]').simulate('change', {
      target: { value: '${ parameters.newDesired }' },
    });

    expect(formik.setFieldValue.calls.allArgs()).toEqual([
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
      page.validate(
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
    const page = new ServerGroupCapacity({ app: {} as any, formik } as any);

    expect(
      page.validate(
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
      page.validate(
        command({
          capacity: { desired: 2 },
          autoscalingPolicy: { minNumReplicas: 3, maxNumReplicas: 4 },
          viewState: { mode: 'create', useSimpleCapacity: false },
        }),
      ),
    ).toEqual({ capacity: { desired: 'Desired capacity must be at least minimum capacity.' } });

    expect(
      page.validate(
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
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} adapter={adapter} />);

    expect(selectOptions(wrapper, 'Zone')).toEqual([
      ['', 'Select...'],
      ['known-zone-a', 'known-zone-a'],
      ['known-zone-b', 'known-zone-b'],
      ['persisted-zone', 'persisted-zone (unavailable)'],
    ]);
    expect(new ServerGroupCapacity({ app: {} as any, formik } as any).validate(command({ zone: null }))).toEqual({
      zone: 'Zone required.',
    });

    wrapper.find('select[aria-label="Zone"]').simulate('change', { target: { value: 'known-zone-b' } });
    await flush();

    const changedCommand = adapter.applyCommandHandler.calls.first().args[0];
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
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} adapter={adapter} />);

    expect(wrapper.find('input[aria-label="Explicit zone distribution"]').prop('checked')).toBe(true);
    expect(wrapper.find('input[aria-label="Preferred zone distribution"]').prop('checked')).toBe(false);
    expect(wrapper.find('input[aria-label="Zone known-zone-a"]').prop('checked')).toBe(true);
    expect(wrapper.find('input[aria-label="Zone persisted-zone"]').prop('checked')).toBe(true);
    expect(wrapper.find('label[htmlFor="gce-capacity-zone-persisted-zone"]').text()).toContain('(unavailable)');
    expect(wrapper.find('select[aria-label="Target shape"]').prop('value')).toBe('EVEN');

    wrapper.find('input[aria-label="Preferred zone distribution"]').simulate('change', { target: { checked: true } });
    await flush();

    const changedCommand = adapter.applyCommandHandler.calls.first().args[0];
    expect(changedCommand.selectZones).toBe(false);
    expect(changedCommand.distributionPolicy.zones).toEqual(['known-zone-a', 'persisted-zone']);
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'selectZonesChanged');
    expect(handlerNames(adapter)).toEqual(['selectZonesChanged', 'zoneChanged']);
  });

  it('serializes the full regional transition cascade against each reconciled command', async () => {
    const transitions = [deferredCommand(), deferredCommand(), deferredCommand(), deferredCommand()];
    const values = command();
    const { adapter, formik } = transitionProps(values, transitions);
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('input[aria-label="Regional server group"]').simulate('change', { target: { checked: true } });
    expect(handlerNames(adapter)).toEqual(['regionalChanged']);

    const afterRegional = command({ regional: true, zone: null, transition: 'regional' });
    transitions[0].resolve(update(afterRegional));
    await flush();
    expect(handlerNames(adapter)).toEqual(['regionalChanged', 'regionChanged']);
    expect(adapter.applyCommandHandler.calls.argsFor(1)[0]).toBe(afterRegional);

    const afterRegion = command({ regional: true, zone: null, transition: 'region' });
    transitions[1].resolve(update(afterRegion));
    await flush();
    expect(handlerNames(adapter)).toEqual(['regionalChanged', 'regionChanged', 'zoneChanged']);
    expect(adapter.applyCommandHandler.calls.argsFor(2)[0]).toBe(afterRegion);

    const afterZone = command({ regional: true, zone: null, transition: 'zone' });
    transitions[2].resolve(update(afterZone));
    await flush();
    expect(handlerNames(adapter)).toEqual(['regionalChanged', 'regionChanged', 'zoneChanged', 'selectZonesChanged']);
    expect(adapter.applyCommandHandler.calls.argsFor(3)[0]).toBe(afterZone);
    expect(formik.setValues).not.toHaveBeenCalled();

    const reconciled = command({ regional: true, zone: null, transition: 'selectZones' });
    transitions[3].resolve(update(reconciled));
    await flush();
    expect(formik.setValues).toHaveBeenCalledWith(reconciled);
  });

  it('serializes zonal and explicit-zone reconciliation handlers', async () => {
    const zonalTransitions = [deferredCommand(), deferredCommand()];
    const zonal = transitionProps(command(), zonalTransitions);
    const zonalWrapper = shallow(<ServerGroupCapacity app={{} as any} formik={zonal.formik} adapter={zonal.adapter} />);

    zonalWrapper.find('select[aria-label="Zone"]').simulate('change', { target: { value: 'known-zone-b' } });
    expect(handlerNames(zonal.adapter)).toEqual(['zoneChanged']);
    const afterZone = command({ zone: 'known-zone-b', transition: 'zone' });
    zonalTransitions[0].resolve(update(afterZone));
    await flush();
    expect(handlerNames(zonal.adapter)).toEqual(['zoneChanged', 'selectZonesChanged']);
    expect(zonal.adapter.applyCommandHandler.calls.argsFor(1)[0]).toBe(afterZone);
    const zonalReconciled = command({ zone: 'known-zone-b', transition: 'selectZones' });
    zonalTransitions[1].resolve(update(zonalReconciled));
    await flush();
    expect(zonal.formik.setValues).toHaveBeenCalledWith(zonalReconciled);

    const regionalTransitions = [deferredCommand(), deferredCommand()];
    const regional = transitionProps(
      command({ regional: true, zone: null, selectZones: true, distributionPolicy: { zones: ['known-zone-a'] } }),
      regionalTransitions,
    );
    const regionalWrapper = shallow(
      <ServerGroupCapacity app={{} as any} formik={regional.formik} adapter={regional.adapter} />,
    );

    regionalWrapper.find('input[aria-label="Zone known-zone-b"]').simulate('change', { target: { checked: true } });
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
    expect(regional.adapter.applyCommandHandler.calls.argsFor(1)[0]).toBe(afterSelectZones);
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
    const wrapper = shallow(<ServerGroupCapacity app={{} as any} formik={formik} />);

    wrapper.find('select[aria-label="Target shape"]').simulate('change', { target: { value: 'ANY' } });

    expect(formik.setFieldValue).toHaveBeenCalledWith('distributionPolicy', { zones: [], targetShape: 'ANY' });
    expect(new ServerGroupCapacity({ app: {} as any, formik } as any).validate(values)).toEqual({});
    expect(
      new ServerGroupCapacity({ app: {} as any, formik } as any).validate({ ...values, selectZones: true }),
    ).toEqual({ distributionPolicy: { zones: 'At least one zone required.' } });
  });
});

function selectOptions(wrapper: ReturnType<typeof shallow>, label: string): string[][] {
  return wrapper
    .find(`select[aria-label="${label}"] option`)
    .map((option) => [option.prop('value') as string, option.text()]);
}

function testProps(values = command()) {
  const formik = ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue').and.callFake((field: string, value: any) => {
      values[field] = value;
    }),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const reconciled = command({ region: 'reconciled-region' });
  const adapter = ({
    applyCommandHandler: jasmine
      .createSpy('applyCommandHandler')
      .and.resolveTo({ command: reconciled, result: { dirty: {} } }),
  } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
  return { adapter, formik, reconciled };
}

function transitionProps(values: IGceServerGroupCommand, transitions: Array<ReturnType<typeof deferredCommand>>) {
  const formik = ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue'),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyCommandHandler: jasmine
      .createSpy('applyCommandHandler')
      .and.callFake(() => transitions[(adapter.applyCommandHandler as jasmine.Spy).calls.count() - 1].promise),
  } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function handlerNames(adapter: jasmine.SpyObj<IGceServerGroupWizardAdapter>): string[] {
  return adapter.applyCommandHandler.calls.allArgs().map((args) => args[1]);
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
