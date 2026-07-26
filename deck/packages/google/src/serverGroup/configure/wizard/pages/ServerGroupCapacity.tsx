import React from 'react';

import type { GceCommandHandlerName, IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';

interface ICapacityValidationErrors {
  autoscalingPolicy?: {
    maxNumReplicas?: string;
    minNumReplicas?: string;
  };
  capacity?: { desired?: string };
  distributionPolicy?: { targetShape?: string; zones?: string };
  zone?: string;
}

interface IZoneOption {
  unavailable: boolean;
  value: string;
}

type CapacityValue = number | string | null | undefined;

const INTEGER_ERROR = 'must be a finite non-negative integer.';

export class ServerGroupCapacity extends GceServerGroupWizardPage {
  public validate(values: IGceServerGroupCommand): ICapacityValidationErrors {
    const errors: ICapacityValidationErrors = {};
    const desired = values.capacity?.desired;

    if (!isValidCapacityValue(desired, values)) {
      errors.capacity = { desired: `Desired capacity ${INTEGER_ERROR}` };
    }

    if (values.autoscalingPolicy) {
      const min = values.autoscalingPolicy?.minNumReplicas;
      const max = values.autoscalingPolicy?.maxNumReplicas;

      if (!isValidCapacityValue(min, values)) {
        errors.autoscalingPolicy = {
          ...errors.autoscalingPolicy,
          minNumReplicas: `Minimum capacity ${INTEGER_ERROR}`,
        };
      }
      if (!isValidCapacityValue(max, values)) {
        errors.autoscalingPolicy = {
          ...errors.autoscalingPolicy,
          maxNumReplicas: `Maximum capacity ${INTEGER_ERROR}`,
        };
      }
      if (isNonNegativeInteger(desired) && isNonNegativeInteger(min) && desired < min) {
        errors.capacity = { desired: 'Desired capacity must be at least minimum capacity.' };
      } else if (isNonNegativeInteger(desired) && isNonNegativeInteger(max) && desired > max) {
        errors.capacity = { desired: 'Desired capacity must not exceed maximum capacity.' };
      }
    }

    if (!values.regional && !values.zone?.trim()) {
      errors.zone = 'Zone required.';
    }
    if (values.regional && values.selectZones && !values.distributionPolicy?.zones?.length) {
      errors.distributionPolicy = { zones: 'At least one zone required.' };
    }

    return errors;
  }

  private commandChanged = (
    command: IGceServerGroupCommand,
    handlers: GceCommandHandlerName[],
  ): Promise<IGceServerGroupCommand | undefined> =>
    this.runLatestCommandRequest(command, async (latestCommand) => {
      let nextCommand = latestCommand;
      for (const handler of handlers) {
        nextCommand = (await this.adapter.applyCommandHandler(nextCommand, handler)).command;
      }
      return nextCommand;
    });

  private capacityChanged = (field: 'desired' | 'max' | 'min', rawValue: string): void => {
    const value = parseCapacity(rawValue, this.props.formik.values);
    if (value === undefined) {
      return;
    }

    const { values } = this.props.formik;
    if (!values.autoscalingPolicy) {
      this.props.formik.setFieldValue('capacity', { min: value, max: value, desired: value });
      return;
    }

    if (field === 'desired') {
      this.props.formik.setFieldValue('capacity', { ...values.capacity, desired: value });
      return;
    }

    const policyField = field === 'min' ? 'minNumReplicas' : 'maxNumReplicas';
    this.props.formik.setFieldValue('autoscalingPolicy', {
      ...values.autoscalingPolicy,
      [policyField]: value,
    });
    this.props.formik.setFieldValue('capacity', { ...values.capacity, [field]: value });
  };

  private capacityModeChanged = (useSimpleCapacity: boolean): void => {
    const { values } = this.props.formik;
    this.props.formik.setFieldValue(
      'overwriteAncestorAutoscalingPolicy',
      useSimpleCapacity && values.viewState?.mode === 'clone' && Boolean(values.autoscalingPolicy),
    );
    this.props.formik.setFieldValue('viewState', { ...values.viewState, useSimpleCapacity });
    this.props.formik.setFieldValue('source', { ...values.source, useSourceCapacity: false });

    if (useSimpleCapacity) {
      this.props.formik.setFieldValue('enableAutoScaling', false);
      this.props.formik.setFieldValue('autoscalingPolicy', null);
      if (isValidCapacityValue(values.capacity?.desired, values)) {
        const desired = values.capacity.desired;
        this.props.formik.setFieldValue('capacity', { min: desired, max: desired, desired });
      }
      return;
    }

    const desired = values.capacity?.desired ?? 1;
    const policy = values.autoscalingPolicy || {
      minNumReplicas: desired,
      maxNumReplicas: desired,
      coolDownPeriodSec: 60,
      cpuUtilization: { utilizationTarget: 0.5 },
    };
    this.props.formik.setFieldValue('enableAutoScaling', true);
    this.props.formik.setFieldValue('autoscalingPolicy', policy);
    this.props.formik.setFieldValue('capacity', {
      ...values.capacity,
      min: policy.minNumReplicas,
      max: policy.maxNumReplicas,
      desired: coherentDesiredCapacity(desired, policy.minNumReplicas, policy.maxNumReplicas),
    });
  };

  private regionalChanged = (regional: boolean): void => {
    void this.commandChanged({ ...this.props.formik.values, regional }, [
      'regionalChanged',
      'regionChanged',
      'zoneChanged',
      'selectZonesChanged',
    ]);
  };

  private zoneChanged = (zone: string): void => {
    void this.commandChanged({ ...this.props.formik.values, zone }, ['zoneChanged', 'selectZonesChanged']);
  };

  private selectZonesChanged = (selectZones: boolean): void => {
    void this.commandChanged({ ...this.props.formik.values, selectZones }, ['selectZonesChanged', 'zoneChanged']);
  };

  private selectedZoneChanged = (zone: string, selected: boolean): void => {
    const values = this.props.formik.values;
    const selectedZones = values.distributionPolicy?.zones || [];
    const zones = selected
      ? Array.from(new Set([...selectedZones, zone]))
      : selectedZones.filter((item) => item !== zone);
    const command = {
      ...values,
      distributionPolicy: { ...values.distributionPolicy, zones },
    };
    void this.commandChanged(command, ['selectZonesChanged', 'zoneChanged']);
  };

  private targetShapeChanged = (targetShape: string): void => {
    const { values } = this.props.formik;
    this.props.formik.setFieldValue('distributionPolicy', { ...values.distributionPolicy, targetShape });
  };

  private renderCapacityInput(label: string, field: 'desired' | 'max' | 'min', value: CapacityValue) {
    const id = `gce-capacity-${field}`;
    const error = capacityError(this.props.formik.errors, field);
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor={id}>
          {label}
        </label>
        <div className="col-md-3">
          <input
            aria-describedby={error ? `${id}-error` : undefined}
            aria-invalid={Boolean(error)}
            aria-label={`${label} capacity`}
            className="form-control input-sm"
            id={id}
            min={0}
            onChange={(event) => this.capacityChanged(field, event.target.value)}
            required={true}
            step={1}
            type={isPipelineMode(this.props.formik.values) ? 'text' : 'number'}
            value={value ?? ''}
          />
          {error && (
            <span className="help-block" id={`${id}-error`} role="alert">
              {error}
            </span>
          )}
        </div>
      </div>
    );
  }

  private renderZonalLocation(): React.ReactNode {
    const values = this.props.formik.values;
    const zones = zoneOptions(values.backingData?.filtered?.zones, values.zone ? [values.zone] : []);
    const error = (this.props.formik.errors as any)?.zone;
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor="gce-capacity-zone">
          Zone
        </label>
        <div className="col-md-7">
          <select
            aria-describedby={error ? 'gce-capacity-zone-error' : undefined}
            aria-invalid={Boolean(error)}
            aria-label="Zone"
            className="form-control input-sm"
            id="gce-capacity-zone"
            onChange={(event) => this.zoneChanged(event.target.value)}
            required={true}
            value={values.zone || ''}
          >
            <option value="">Select...</option>
            {zones.map(({ unavailable, value }) => (
              <option key={value} value={value}>
                {value}
                {unavailable ? ' (unavailable)' : ''}
              </option>
            ))}
          </select>
          {error && (
            <span className="help-block" id="gce-capacity-zone-error" role="alert">
              {error}
            </span>
          )}
        </div>
      </div>
    );
  }

  private renderRegionalDistribution(): React.ReactNode {
    const values = this.props.formik.values;
    const selectedZones = values.distributionPolicy?.zones || [];
    const zones = zoneOptions(values.backingData?.filtered?.zones, selectedZones);
    const errors = this.props.formik.errors as any;
    const zonesError = errors?.distributionPolicy?.zones;
    const targetShapes: string[] = values.backingData?.distributionPolicyTargetShapes || ['ANY', 'EVEN'];

    return (
      <div>
        <fieldset className="form-group">
          <legend className="col-md-3 sm-label-right">Distribution</legend>
          <div className="col-md-7">
            <label className="radio-inline" htmlFor="gce-capacity-preferred-zones">
              <input
                aria-label="Preferred zone distribution"
                checked={!values.selectZones}
                id="gce-capacity-preferred-zones"
                name="gce-capacity-zone-distribution"
                onChange={(event) => event.target.checked && this.selectZonesChanged(false)}
                type="radio"
              />
              Preferred zones
            </label>
            <label className="radio-inline" htmlFor="gce-capacity-explicit-zones">
              <input
                aria-label="Explicit zone distribution"
                checked={Boolean(values.selectZones)}
                id="gce-capacity-explicit-zones"
                name="gce-capacity-zone-distribution"
                onChange={(event) => event.target.checked && this.selectZonesChanged(true)}
                type="radio"
              />
              Select zones explicitly
            </label>
          </div>
        </fieldset>

        {values.selectZones && (
          <fieldset aria-describedby={zonesError ? 'gce-capacity-zones-error' : undefined} className="form-group">
            <legend className="col-md-3 sm-label-right">Zones</legend>
            <div aria-invalid={Boolean(zonesError)} className="col-md-7">
              {zones.map(({ unavailable, value }) => {
                const id = `gce-capacity-zone-${value}`;
                return (
                  <div className="checkbox" key={value}>
                    <label htmlFor={id}>
                      <input
                        aria-label={`Zone ${value}`}
                        checked={selectedZones.includes(value)}
                        id={id}
                        onChange={(event) => this.selectedZoneChanged(value, event.target.checked)}
                        type="checkbox"
                      />
                      {value}
                      {unavailable ? ' (unavailable)' : ''}
                    </label>
                  </div>
                );
              })}
              {zonesError && (
                <span className="help-block" id="gce-capacity-zones-error" role="alert">
                  {zonesError}
                </span>
              )}
            </div>
          </fieldset>
        )}

        <div className="form-group">
          <label className="col-md-3 sm-label-right" htmlFor="gce-capacity-target-shape">
            Target shape
          </label>
          <div className="col-md-3">
            <select
              aria-label="Target shape"
              className="form-control input-sm"
              id="gce-capacity-target-shape"
              onChange={(event) => this.targetShapeChanged(event.target.value)}
              value={values.distributionPolicy?.targetShape || ''}
            >
              <option value="">Select...</option>
              {targetShapes.map((targetShape) => (
                <option key={targetShape} value={targetShape}>
                  {targetShape}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>
    );
  }

  public render(): JSX.Element {
    const { values } = this.props.formik;
    const useSimpleCapacity = !values.autoscalingPolicy;

    return (
      <div className="container-fluid form-horizontal">
        <fieldset className="form-group">
          <legend className="col-md-3 sm-label-right">Capacity mode</legend>
          <div className="col-md-7">
            <label className="radio-inline" htmlFor="gce-capacity-simple">
              <input
                aria-label="Simple capacity"
                checked={useSimpleCapacity}
                id="gce-capacity-simple"
                name="gce-capacity-mode"
                onChange={(event) => event.target.checked && this.capacityModeChanged(true)}
                type="radio"
              />
              Simple
            </label>
            <label className="radio-inline" htmlFor="gce-capacity-autoscaling">
              <input
                aria-label="Autoscaling capacity"
                checked={!useSimpleCapacity}
                id="gce-capacity-autoscaling"
                name="gce-capacity-mode"
                onChange={(event) => event.target.checked && this.capacityModeChanged(false)}
                type="radio"
              />
              Autoscaling
            </label>
          </div>
        </fieldset>

        {!useSimpleCapacity && this.renderCapacityInput('Minimum', 'min', values.autoscalingPolicy?.minNumReplicas)}
        {!useSimpleCapacity && this.renderCapacityInput('Maximum', 'max', values.autoscalingPolicy?.maxNumReplicas)}
        {this.renderCapacityInput('Desired', 'desired', values.capacity?.desired)}

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Regional</b>
          </div>
          <div className="col-md-7 checkbox">
            <label htmlFor="gce-capacity-regional">
              <input
                aria-label="Regional server group"
                checked={values.regional}
                id="gce-capacity-regional"
                onChange={(event) => this.regionalChanged(event.target.checked)}
                type="checkbox"
              />
              Distribute instances across multiple zones
            </label>
          </div>
        </div>

        {values.regional ? this.renderRegionalDistribution() : this.renderZonalLocation()}
      </div>
    );
  }
}

function capacityError(errors: unknown, field: 'desired' | 'max' | 'min'): string | undefined {
  if (field === 'desired') {
    return (errors as any)?.capacity?.desired;
  }
  return (errors as any)?.autoscalingPolicy?.[field === 'min' ? 'minNumReplicas' : 'maxNumReplicas'];
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Number.isInteger(value) && value >= 0;
}

function parseCapacity(rawValue: string, values: IGceServerGroupCommand): number | string | undefined {
  if (!rawValue.trim()) {
    return undefined;
  }
  if (isPipelineMode(values) && isExpression(rawValue)) {
    return rawValue;
  }
  const value = Number(rawValue);
  return isNonNegativeInteger(value) ? value : undefined;
}

function isValidCapacityValue(value: unknown, values: IGceServerGroupCommand): value is number | string {
  return isNonNegativeInteger(value) || (isPipelineMode(values) && isExpression(value));
}

function isPipelineMode(values: IGceServerGroupCommand): boolean {
  return values.viewState.mode === 'createPipeline' || values.viewState.mode === 'editPipeline';
}

function isExpression(value: unknown): value is string {
  return typeof value === 'string' && /^\s*\$\{.+\}\s*$/.test(value);
}

function coherentDesiredCapacity(desired: any, min: any, max: any): any {
  if (typeof desired === 'number' && typeof min === 'number' && typeof max === 'number') {
    return Math.min(max, Math.max(min, desired));
  }
  return desired ?? min ?? max ?? 1;
}

function zoneOptions(rawZones: readonly string[] | null | undefined, selectedZones: readonly string[]): IZoneOption[] {
  const availableZones = rawZones || [];
  return Array.from(new Set([...availableZones, ...selectedZones])).map((value) => ({
    unavailable: !availableZones.includes(value),
    value,
  }));
}
