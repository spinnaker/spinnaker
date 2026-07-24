import React from 'react';

import { DeckRuntimeContext, HelpField, SpelService } from '@spinnaker/core';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';

const MIN_BOOT_DISK_SIZE_GB = 10;
const MAX_BOOT_DISK_SIZE_GB = 65536;

interface IGceAcceleratorConfig {
  acceleratorCount: number | string;
  acceleratorType: string;
}

interface IGceAcceleratorType {
  availableCardCounts: number[];
  description?: string;
  name: string;
}

interface IGceCustomInstanceConfig {
  extendedMemory: boolean;
  instanceFamily: string;
  memory: number;
  vCpuCount: number;
}

interface IGceDisk {
  sizeGb: number | string;
  sourceImage?: string;
  type: string;
}

interface IGceInstanceTypeValidationErrors {
  acceleratorConfigs?: string;
  disks?: string;
  instanceType?: string;
}

export class ServerGroupInstanceType extends GceServerGroupWizardPage {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private instanceTypeRequestGeneration = 0;

  public validate(values: IGceServerGroupCommand): IGceInstanceTypeValidationErrors {
    const errors: IGceInstanceTypeValidationErrors = {};
    if (!values.instanceType) {
      errors.instanceType = 'Machine type required.';
    }

    const bootDisk = getBootDisk(values);
    if (
      !bootDisk?.type ||
      !isValidBoundedValue(bootDisk.sizeGb, values, MIN_BOOT_DISK_SIZE_GB, MAX_BOOT_DISK_SIZE_GB)
    ) {
      errors.disks = `Boot disk size must be between ${MIN_BOOT_DISK_SIZE_GB} and ${MAX_BOOT_DISK_SIZE_GB} GB.`;
    }

    const accelerators = getAcceleratorConfigs(values);
    const availableAccelerators = getAvailableAccelerators(values);
    if (
      accelerators.some((config) => {
        if (!config.acceleratorType || !isValidPositiveInteger(config.acceleratorCount, values)) {
          return true;
        }
        const available = availableAccelerators.find((accelerator) => accelerator.name === config.acceleratorType);
        return available ? !available.availableCardCounts.includes(Number(config.acceleratorCount)) : false;
      })
    ) {
      errors.acceleratorConfigs = 'Accelerator count is unavailable for the selected type.';
    }

    return errors;
  }

  private publish = (nextValues: IGceServerGroupCommand): void => {
    this.props.formik.setValues(nextValues);
  };

  private updateValues = (changes: Partial<IGceServerGroupCommand>): void => {
    this.publish({ ...this.props.formik.values, ...changes });
  };

  private updateMachineType = (instanceType: string): void => {
    const values = this.props.formik.values;
    const nextValues = {
      ...values,
      instanceType,
      viewState: { ...values.viewState, instanceProfile: 'custom' },
    };
    const requestGeneration = ++this.instanceTypeRequestGeneration;
    this.publish(nextValues);
    if (isPipelineMode(values) && SpelService.includesSpel(instanceType)) {
      return;
    }
    void this.runLatestCommandRequest(nextValues, async (latestCommand) => {
      const instanceTypeDetails = await this.context.services.instanceTypeService.getInstanceTypeDetails(
        latestCommand.selectedProvider || 'gce',
        instanceType,
      );
      if (requestGeneration !== this.instanceTypeRequestGeneration) {
        return latestCommand;
      }
      const reconciledCommand = reconcileInstanceTypeDetails(latestCommand, instanceTypeDetails);
      const update = await this.adapter.applyCommandHandler(reconciledCommand, 'zoneChanged');
      return requestGeneration === this.instanceTypeRequestGeneration ? update.command : latestCommand;
    });
  };

  private updateMachineTypeMode = (custom: boolean): void => {
    const values = this.props.formik.values;
    if (!custom) {
      this.publish({ ...values, viewState: { ...values.viewState, instanceProfile: 'custom' } });
      return;
    }
    this.updateCustomInstance(getCustomInstanceConfig(values));
  };

  private updateCustomInstance = (customInstance: IGceCustomInstanceConfig): void => {
    this.instanceTypeRequestGeneration++;
    const values = this.props.formik.values;
    const nextValues = {
      ...values,
      instanceType: buildCustomMachineType(customInstance),
      viewState: { ...values.viewState, customInstance, instanceProfile: 'buildCustom' },
    };
    void this.runLatestCommandRequest(nextValues, async (latestCommand) => {
      const update = await this.adapter.applyCommandHandler(latestCommand, 'customInstanceChanged');
      return update.command;
    });
  };

  private updatePreemptible = (preemptible: boolean): void => {
    this.updateValues({
      preemptible,
      automaticRestart: !preemptible,
      onHostMaintenance: preemptible ? 'TERMINATE' : 'MIGRATE',
    });
  };

  private updateAcceleratorType = (index: number, acceleratorType: string): void => {
    const values = this.props.formik.values;
    const accelerators = getAcceleratorConfigs(values);
    const available = getAvailableAccelerators(values).find((candidate) => candidate.name === acceleratorType);
    const currentCount = Number(accelerators[index].acceleratorCount);
    const acceleratorCount = available
      ? nearestAvailableCount(available.availableCardCounts, currentCount)
      : currentCount;
    this.updateValues({
      acceleratorConfigs: accelerators.map((config, configIndex) =>
        configIndex === index ? { acceleratorType, acceleratorCount } : config,
      ),
    });
  };

  private updateAcceleratorCount = (index: number, rawValue: string): void => {
    const accelerators = getAcceleratorConfigs(this.props.formik.values);
    this.updateValues({
      acceleratorConfigs: accelerators.map((config, configIndex) =>
        configIndex === index ? { ...config, acceleratorCount: parseNumericOrExpression(rawValue) } : config,
      ),
    });
  };

  private addAccelerator = (): void => {
    const values = this.props.formik.values;
    const available = getAvailableAccelerators(values)[0];
    if (!available) {
      return;
    }
    this.updateValues({
      acceleratorConfigs: getAcceleratorConfigs(values).concat({
        acceleratorType: available.name,
        acceleratorCount: available.availableCardCounts[0] || 1,
      }),
    });
  };

  private removeAccelerator = (index: number): void => {
    this.updateValues({
      acceleratorConfigs: getAcceleratorConfigs(this.props.formik.values).filter(
        (_config, configIndex) => configIndex !== index,
      ),
    });
  };

  private updateBootDisk = (changes: Partial<IGceDisk>): void => {
    const values = this.props.formik.values;
    const disks = ([...(values.disks || [])] as unknown) as IGceDisk[];
    const bootDiskIndex = getBootDiskIndex(disks);
    if (bootDiskIndex === -1) {
      disks.unshift({ type: 'pd-ssd', sizeGb: MIN_BOOT_DISK_SIZE_GB, ...changes });
    } else {
      disks[bootDiskIndex] = { ...disks[bootDiskIndex], ...changes };
    }
    this.updateValues({ disks });
  };

  public render(): JSX.Element {
    const values = this.props.formik.values;
    const errors = (this.props.formik.errors || {}) as IGceInstanceTypeValidationErrors;
    const customMode = values.viewState.instanceProfile === 'buildCustom';
    const pipelineMode = isPipelineMode(values);
    const machineTypes = preserveStringReference(
      values.backingData?.filtered?.instanceTypes || [],
      values.instanceType,
    );
    const cpuPlatforms = preserveStringReference(
      values.backingData?.filtered?.cpuPlatforms || ['(Automatic)'],
      values.minCpuPlatform,
    );
    const custom = getCustomInstanceConfig(values);
    const customChoices = values.backingData?.customInstanceTypes || {};
    const bootDisk = getBootDisk(values) || { type: 'pd-ssd', sizeGb: MIN_BOOT_DISK_SIZE_GB };
    const diskTypes = preserveStringReference(values.backingData?.persistentDiskTypes || [], bootDisk.type);
    const availableAccelerators = getAvailableAccelerators(values);
    const acceleratorConfigs = getAcceleratorConfigs(values);

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-5 sm-label-right">
            <b>Machine type</b>
          </div>
          <div className="col-md-6">
            <label className="radio-inline" htmlFor="gce-machine-type-standard">
              <input
                checked={!customMode}
                id="gce-machine-type-standard"
                name="gce-machine-type-mode"
                onChange={() => this.updateMachineTypeMode(false)}
                type="radio"
              />
              Standard
            </label>
            <label className="radio-inline" htmlFor="gce-machine-type-custom">
              <input
                checked={customMode}
                id="gce-machine-type-custom"
                name="gce-machine-type-mode"
                onChange={() => this.updateMachineTypeMode(true)}
                type="radio"
              />
              Custom CPU and memory
            </label>
          </div>
        </div>

        {!customMode && (
          <div className="form-group">
            <label className="col-md-5 sm-label-right" htmlFor="gce-machine-type">
              Machine type
            </label>
            <div className="col-md-6">
              {pipelineMode ? (
                <input
                  aria-describedby={errors.instanceType ? 'gce-machine-type-error' : undefined}
                  aria-invalid={Boolean(errors.instanceType)}
                  aria-label="Machine type"
                  className="form-control input-sm"
                  id="gce-machine-type"
                  onChange={(event) => this.updateMachineType(event.target.value)}
                  type="text"
                  value={values.instanceType || ''}
                />
              ) : (
                <select
                  aria-describedby={errors.instanceType ? 'gce-machine-type-error' : undefined}
                  aria-invalid={Boolean(errors.instanceType)}
                  aria-label="Machine type"
                  className="form-control input-sm"
                  id="gce-machine-type"
                  onChange={(event) => this.updateMachineType(event.target.value)}
                  required={true}
                  value={values.instanceType || ''}
                >
                  <option value="">Select...</option>
                  {machineTypes.map(({ unavailable, value }) => (
                    <option key={value} value={value}>
                      {value}
                      {unavailable ? ' (Unavailable)' : ''}
                    </option>
                  ))}
                </select>
              )}
              {renderValidationError(errors.instanceType, 'gce-machine-type-error')}
            </div>
          </div>
        )}

        {customMode && (
          <>
            {this.renderSelect(
              'gce-custom-family',
              'Machine family',
              custom.instanceFamily,
              preserveNumberOrStringReference(
                customChoices.instanceFamilyList || ['N1', 'E2', 'N2', 'N2D'],
                custom.instanceFamily,
              ),
              (value) =>
                this.updateCustomInstance({
                  ...custom,
                  extendedMemory: value === 'E2' ? false : custom.extendedMemory,
                  instanceFamily: value,
                }),
            )}
            {this.renderSelect(
              'gce-custom-cpu',
              'vCPU count',
              custom.vCpuCount,
              preserveNumberOrStringReference(customChoices.vCpuList || [], custom.vCpuCount),
              (value) => this.updateCustomInstance({ ...custom, vCpuCount: Number(value) }),
              errors.instanceType,
              'gce-machine-type-error',
            )}
            {this.renderSelect(
              'gce-custom-memory',
              'Memory (GB)',
              custom.memory,
              preserveNumberOrStringReference(customChoices.memoryList || [], custom.memory),
              (value) => this.updateCustomInstance({ ...custom, memory: Number(value) }),
              errors.instanceType,
              'gce-machine-type-error',
            )}
            {custom.instanceFamily !== 'E2' && (
              <div className="form-group">
                <div className="col-md-offset-5 col-md-6 checkbox">
                  <label htmlFor="gce-custom-extended-memory">
                    <input
                      checked={custom.extendedMemory}
                      id="gce-custom-extended-memory"
                      onChange={(event) =>
                        this.updateCustomInstance({ ...custom, extendedMemory: event.target.checked })
                      }
                      type="checkbox"
                    />{' '}
                    Extended memory
                  </label>
                </div>
              </div>
            )}
            {renderValidationError(errors.instanceType, 'gce-machine-type-error')}
          </>
        )}

        <div className="form-group">
          <label className="col-md-5 sm-label-right" htmlFor="gce-min-cpu-platform">
            Minimum CPU platform <HelpField id="gce.serverGroup.minCpuPlatform" />
          </label>
          <div className="col-md-6">
            {pipelineMode ? (
              <input
                aria-label="Minimum CPU platform"
                className="form-control input-sm"
                id="gce-min-cpu-platform"
                onChange={(event) => this.updateValues({ minCpuPlatform: event.target.value })}
                type="text"
                value={values.minCpuPlatform || ''}
              />
            ) : (
              <select
                aria-label="Minimum CPU platform"
                className="form-control input-sm"
                id="gce-min-cpu-platform"
                onChange={(event) => this.updateValues({ minCpuPlatform: event.target.value })}
                value={values.minCpuPlatform || ''}
              >
                {cpuPlatforms.map(({ unavailable, value }) => (
                  <option key={value} value={value}>
                    {value}
                    {unavailable ? ' (Unavailable)' : ''}
                  </option>
                ))}
              </select>
            )}
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-offset-5 col-md-6 checkbox">
            <label htmlFor="gce-preemptible">
              <input
                checked={Boolean(values.preemptible)}
                id="gce-preemptible"
                onChange={(event) => this.updatePreemptible(event.target.checked)}
                type="checkbox"
              />{' '}
              Spot (preemptible) instances <HelpField id="gce.serverGroup.preemptibility" />
            </label>
          </div>
        </div>

        <fieldset>
          <legend className="sm-label-left">Accelerators (GPUs)</legend>
          {acceleratorConfigs.map((config, index) => {
            const selectedAccelerator = availableAccelerators.find(
              (accelerator) => accelerator.name === config.acceleratorType,
            );
            const typeOptions = preserveAcceleratorReference(availableAccelerators, config.acceleratorType);
            const countOptions = preserveNumberOrStringReference(
              selectedAccelerator?.availableCardCounts || [],
              config.acceleratorCount,
            );
            return (
              <div className="form-group" key={`${config.acceleratorType}-${index}`}>
                <label className="col-md-3 sm-label-right" htmlFor={`gce-accelerator-type-${index}`}>
                  GPU type
                </label>
                <div className="col-md-4">
                  <select
                    aria-describedby={errors.acceleratorConfigs ? 'gce-accelerators-error' : undefined}
                    aria-invalid={Boolean(errors.acceleratorConfigs)}
                    aria-label={`GPU type ${index + 1}`}
                    className="form-control input-sm"
                    id={`gce-accelerator-type-${index}`}
                    onChange={(event) => this.updateAcceleratorType(index, event.target.value)}
                    value={config.acceleratorType}
                  >
                    {typeOptions.map(({ description, name, unavailable }) => (
                      <option key={name} value={name}>
                        {unavailable ? name : description || name}
                        {unavailable ? ' (Unavailable)' : ''}
                      </option>
                    ))}
                  </select>
                </div>
                <label className="col-md-1 sm-label-right" htmlFor={`gce-accelerator-count-${index}`}>
                  Count
                </label>
                <div className="col-md-2">
                  {pipelineMode ? (
                    <input
                      aria-describedby={errors.acceleratorConfigs ? 'gce-accelerators-error' : undefined}
                      aria-invalid={Boolean(errors.acceleratorConfigs)}
                      aria-label={`GPU count ${index + 1}`}
                      className="form-control input-sm"
                      id={`gce-accelerator-count-${index}`}
                      onChange={(event) => this.updateAcceleratorCount(index, event.target.value)}
                      type="text"
                      value={config.acceleratorCount}
                    />
                  ) : (
                    <select
                      aria-describedby={errors.acceleratorConfigs ? 'gce-accelerators-error' : undefined}
                      aria-invalid={Boolean(errors.acceleratorConfigs)}
                      aria-label={`GPU count ${index + 1}`}
                      className="form-control input-sm"
                      id={`gce-accelerator-count-${index}`}
                      onChange={(event) => this.updateAcceleratorCount(index, event.target.value)}
                      value={config.acceleratorCount}
                    >
                      {countOptions.map(({ unavailable, value }) => (
                        <option key={String(value)} value={value}>
                          {value}
                          {unavailable ? ' (Unavailable)' : ''}
                        </option>
                      ))}
                    </select>
                  )}
                </div>
                <div className="col-md-1">
                  <button
                    aria-label={`Remove GPU ${index + 1}`}
                    className="btn btn-link"
                    onClick={() => this.removeAccelerator(index)}
                    type="button"
                  >
                    <span aria-hidden="true" className="glyphicon glyphicon-trash" />
                  </button>
                </div>
              </div>
            );
          })}
          <button
            className="btn btn-block btn-sm add-new"
            disabled={!availableAccelerators.length}
            onClick={this.addAccelerator}
            type="button"
          >
            <span aria-hidden="true" className="glyphicon glyphicon-plus-sign" /> Add accelerator
          </button>
          {renderValidationError(errors.acceleratorConfigs, 'gce-accelerators-error')}
        </fieldset>

        <fieldset>
          <legend className="sm-label-left">Boot disk</legend>
          <div className="form-group">
            <label className="col-md-5 sm-label-right" htmlFor="gce-boot-disk-type">
              Type
            </label>
            <div className="col-md-6">
              <select
                aria-describedby={errors.disks ? 'gce-boot-disk-error' : undefined}
                aria-invalid={Boolean(errors.disks)}
                aria-label="Boot disk type"
                className="form-control input-sm"
                id="gce-boot-disk-type"
                onChange={(event) => this.updateBootDisk({ type: event.target.value })}
                value={bootDisk.type}
              >
                {diskTypes.map(({ unavailable, value }) => (
                  <option key={value} value={value}>
                    {value}
                    {unavailable ? ' (Unavailable)' : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="col-md-5 sm-label-right" htmlFor="gce-boot-disk-size">
              Size (GB)
            </label>
            <div className="col-md-3">
              <input
                aria-describedby={errors.disks ? 'gce-boot-disk-error' : undefined}
                aria-invalid={Boolean(errors.disks)}
                aria-label="Boot disk size"
                className="form-control input-sm"
                id="gce-boot-disk-size"
                max={pipelineMode ? undefined : MAX_BOOT_DISK_SIZE_GB}
                min={pipelineMode ? undefined : MIN_BOOT_DISK_SIZE_GB}
                onChange={(event) => this.updateBootDisk({ sizeGb: parseNumericOrExpression(event.target.value) })}
                required={true}
                type={pipelineMode ? 'text' : 'number'}
                value={bootDisk.sizeGb}
              />
              {renderValidationError(errors.disks, 'gce-boot-disk-error')}
            </div>
          </div>
        </fieldset>
      </div>
    );
  }

  private renderSelect(
    id: string,
    label: string,
    selected: number | string,
    options: Array<{ unavailable: boolean; value: number | string }>,
    onChange: (value: string) => void,
    error?: string,
    errorId?: string,
  ): JSX.Element {
    return (
      <div className="form-group">
        <label className="col-md-5 sm-label-right" htmlFor={id}>
          {label}
        </label>
        <div className="col-md-3">
          <select
            aria-describedby={error ? errorId : undefined}
            aria-invalid={Boolean(error)}
            aria-label={label}
            className="form-control input-sm"
            id={id}
            onChange={(event) => onChange(event.target.value)}
            value={selected}
          >
            {options.map(({ unavailable, value }) => (
              <option key={String(value)} value={value}>
                {value}
                {unavailable ? ' (Unavailable)' : ''}
              </option>
            ))}
          </select>
        </div>
      </div>
    );
  }
}

function renderValidationError(error: string | undefined, id: string): React.ReactNode {
  return (
    error && (
      <span className="help-block" id={id} role="alert">
        {error}
      </span>
    )
  );
}

function isPipelineMode(values: IGceServerGroupCommand): boolean {
  return values.viewState.mode === 'createPipeline' || values.viewState.mode === 'editPipeline';
}

function isExpression(value: unknown): value is string {
  return typeof value === 'string' && /^\s*\$\{.+\}\s*$/.test(value);
}

function isValidBoundedValue(value: unknown, command: IGceServerGroupCommand, min: number, max: number): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) && Number.isInteger(numberValue) && numberValue >= min && numberValue <= max;
}

function isValidPositiveInteger(value: unknown, command: IGceServerGroupCommand): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) && Number.isInteger(numberValue) && numberValue > 0;
}

function parseNumericOrExpression(value: string): number | string {
  return isExpression(value) ? value : Number(value);
}

function preserveStringReference(
  availableValues: string[],
  selectedValue?: string | null,
): Array<{ unavailable: boolean; value: string }> {
  const options = availableValues.map((value) => ({ unavailable: false, value }));
  if (selectedValue && !availableValues.includes(selectedValue)) {
    options.push({ unavailable: true, value: selectedValue });
  }
  return options;
}

function preserveNumberOrStringReference(
  availableValues: Array<number | string>,
  selectedValue: number | string,
): Array<{ unavailable: boolean; value: number | string }> {
  const options = availableValues.map((value) => ({ unavailable: false, value }));
  if (!availableValues.some((value) => String(value) === String(selectedValue))) {
    options.push({ unavailable: true, value: selectedValue });
  }
  return options;
}

function preserveAcceleratorReference(
  availableAccelerators: IGceAcceleratorType[],
  selectedType: string,
): Array<IGceAcceleratorType & { unavailable: boolean }> {
  const options = availableAccelerators.map((accelerator) => ({ ...accelerator, unavailable: false }));
  if (selectedType && !availableAccelerators.some((accelerator) => accelerator.name === selectedType)) {
    options.push({ availableCardCounts: [], name: selectedType, unavailable: true });
  }
  return options;
}

function getAvailableAccelerators(values: IGceServerGroupCommand): IGceAcceleratorType[] {
  return values.viewState.acceleratorTypes || [];
}

function getAcceleratorConfigs(values: IGceServerGroupCommand): IGceAcceleratorConfig[] {
  return values.acceleratorConfigs || [];
}

function nearestAvailableCount(availableCounts: number[], currentCount: number): number {
  if (!availableCounts.length) {
    return 1;
  }
  if (availableCounts.includes(currentCount)) {
    return currentCount;
  }
  return availableCounts.reduce(
    (nearest, count) => (count <= currentCount && count > nearest ? count : nearest),
    availableCounts[0],
  );
}

function getBootDiskIndex(disks: IGceDisk[]): number {
  return disks.findIndex((disk) => disk.type.startsWith('pd-') || disk.type.startsWith('hyperdisk-'));
}

function getBootDisk(values: IGceServerGroupCommand): IGceDisk | undefined {
  const disks = ((values.disks || []) as unknown) as IGceDisk[];
  return disks[getBootDiskIndex(disks)];
}

function reconcileInstanceTypeDetails(
  command: IGceServerGroupCommand,
  instanceTypeDetails: any,
): IGceServerGroupCommand {
  const defaultDisks = instanceTypeDetails?.storage?.defaultSettings?.disks;
  const viewState = { ...command.viewState };
  if (defaultDisks) {
    delete viewState.overriddenStorageDescription;
  }
  return {
    ...command,
    ...(defaultDisks ? { disks: defaultDisks.map((disk: IGceDisk) => ({ ...disk })) } : {}),
    viewState: {
      ...viewState,
      instanceTypeDetails,
    },
  };
}

function getCustomInstanceConfig(values: IGceServerGroupCommand): IGceCustomInstanceConfig {
  const configured = values.viewState.customInstance || {};
  const parsed = parseCustomMachineType(values.instanceType);
  const choices = values.backingData?.customInstanceTypes || {};
  return {
    extendedMemory: configured.extendedMemory ?? parsed.extendedMemory,
    instanceFamily: configured.instanceFamily || parsed.instanceFamily || choices.instanceFamilyList?.[0] || 'N1',
    memory: configured.memory || parsed.memory || choices.memoryList?.[0] || 1,
    vCpuCount: configured.vCpuCount || parsed.vCpuCount || choices.vCpuList?.[0] || 1,
  };
}

function parseCustomMachineType(instanceType?: string | null): Partial<IGceCustomInstanceConfig> {
  if (!instanceType || !instanceType.includes('custom-')) {
    return { extendedMemory: false };
  }
  const match = /^(?:(n1|e2|n2|n2d)-)?custom-(\d+)-(\d+)(-ext)?$/i.exec(instanceType);
  if (!match) {
    return { extendedMemory: false };
  }
  return {
    extendedMemory: Boolean(match[4]),
    instanceFamily: (match[1] || 'N1').toUpperCase(),
    memory: Number(match[3]) / 1024,
    vCpuCount: Number(match[2]),
  };
}

function buildCustomMachineType(config: IGceCustomInstanceConfig): string {
  const family = config.instanceFamily.toLowerCase();
  const prefix = family === 'n1' ? '' : `${family}-`;
  return `${prefix}custom-${config.vCpuCount}-${Number(config.memory) * 1024}${config.extendedMemory ? '-ext' : ''}`;
}
