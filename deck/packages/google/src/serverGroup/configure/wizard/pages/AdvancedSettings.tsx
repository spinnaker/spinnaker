import React from 'react';

import { HelpField, MapEditor } from '@spinnaker/core';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';

function optionsWithPersistedValue(options: string[], persistedValue?: string): string[] {
  return Array.from(new Set([...options, persistedValue].filter(Boolean)) as Set<string>);
}

function hasEmptyKey(map: Record<string, unknown> = {}): boolean {
  return Object.keys(map).some((key) => !key.trim());
}

function hasEmptyValue(map: Record<string, unknown> = {}): boolean {
  return Object.values(map).some((value) => typeof value !== 'string' || !value.trim());
}

function isPipelineMode(values: IGceServerGroupCommand): boolean {
  return values.viewState.mode === 'createPipeline' || values.viewState.mode === 'editPipeline';
}

function isExpression(value: unknown): value is string {
  return typeof value === 'string' && /^\s*\$\{.+\}\s*$/.test(value);
}

function parseNumericOrExpression(value: string): number | string {
  return isExpression(value) ? value : Number(value);
}

function isValidInteger(
  value: unknown,
  minimum: number,
  values: IGceServerGroupCommand,
  maximum = Number.MAX_SAFE_INTEGER,
): boolean {
  if (isPipelineMode(values) && isExpression(value)) {
    return true;
  }
  const numericValue = Number(value);
  return (
    Number.isFinite(numericValue) &&
    Number.isInteger(numericValue) &&
    numericValue >= minimum &&
    numericValue <= maximum
  );
}

function isValidAccelerator(accelerator: any, values: IGceServerGroupCommand): boolean {
  if (!accelerator.acceleratorType || !isValidInteger(accelerator.acceleratorCount, 1, values)) {
    return false;
  }
  if (isPipelineMode(values) && isExpression(accelerator.acceleratorCount)) {
    return true;
  }
  const available = (values.viewState?.acceleratorTypes || []).find(
    (candidate: any) => candidate.name === accelerator.acceleratorType,
  );
  return available ? available.availableCardCounts.includes(Number(accelerator.acceleratorCount)) : true;
}

function getLocalSsds(values: IGceServerGroupCommand): any[] {
  return (values.disks || []).filter((disk: any) => disk.type === 'local-ssd');
}

function getPersistentDisks(values: IGceServerGroupCommand): any[] {
  return (values.disks || []).filter((disk: any) => disk.type !== 'local-ssd');
}

function renderValidationError(error: string, id: string): React.ReactNode {
  return (
    error && (
      <span className="help-block" id={id} role="alert">
        {error}
      </span>
    )
  );
}

export class AdvancedSettings extends GceServerGroupWizardPage {
  public validate(values: IGceServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};
    if (getPersistentDisks(values).some((disk: any) => !disk.type || !isValidInteger(disk.sizeGb, 10, values, 65536))) {
      errors.disks = 'Every persistent disk requires a type and an integer size between 10 and 65536 GB.';
    }
    if ((values.acceleratorConfigs || []).some((accelerator: any) => !isValidAccelerator(accelerator, values))) {
      errors.acceleratorConfigs = 'Every accelerator requires a type and a supported positive integer count.';
    }
    if (hasEmptyKey(values.instanceMetadata)) {
      errors.instanceMetadata = 'Metadata keys cannot be empty.';
    }
    if (hasEmptyKey(values.labels) || hasEmptyValue(values.labels)) {
      errors.labels = hasEmptyKey(values.labels) ? 'Label keys cannot be empty.' : 'Label values cannot be empty.';
    }
    if (hasEmptyKey(values.resourceManagerTags) || hasEmptyValue(values.resourceManagerTags)) {
      errors.resourceManagerTags = hasEmptyKey(values.resourceManagerTags)
        ? 'Resource Manager tag keys cannot be empty.'
        : 'Resource Manager tag values cannot be empty.';
    }
    if ((values.tags || []).some((tag: any) => !(typeof tag === 'string' ? tag : tag.value)?.trim())) {
      errors.tags = 'Network tags cannot be empty.';
    }
    if ((values.authScopes || []).some((scope: string) => !scope.trim())) {
      errors.authScopes = 'Auth scopes cannot be empty.';
    }
    if (values.enableConfidentialCompute && 'confidentialInstanceType' in values && !values.confidentialInstanceType) {
      errors.confidentialInstanceType = 'Confidential instance type required.';
    }
    return errors;
  }

  private setField = (field: string, value: any): void => {
    this.props.formik.setFieldValue(field, value);
  };

  private updateDisk = (index: number, changes: Record<string, unknown>): void => {
    const values = this.props.formik.values;
    const persistentDisks = getPersistentDisks(values);
    this.setField(
      'disks',
      persistentDisks
        .map((disk: any, diskIndex: number) => (diskIndex === index ? { ...disk, ...changes } : disk))
        .concat(getLocalSsds(values)),
    );
  };

  private removeDisk = (index: number): void => {
    const values = this.props.formik.values;
    this.setField(
      'disks',
      getPersistentDisks(values)
        .filter((_disk: any, diskIndex: number) => diskIndex !== index)
        .concat(getLocalSsds(values)),
    );
  };

  private updateLocalSsdCount = (count: number): void => {
    const values = this.props.formik.values;
    const localSsds = getLocalSsds(values).slice(0, count);
    while (localSsds.length < count) {
      localSsds.push({ type: 'local-ssd', sizeGb: 375 });
    }
    this.setField('disks', getPersistentDisks(values).concat(localSsds));
  };

  private updateAccelerator = (index: number, changes: Record<string, unknown>): void => {
    const accelerators = this.props.formik.values.acceleratorConfigs || [];
    this.setField(
      'acceleratorConfigs',
      accelerators.map((accelerator: any, acceleratorIndex: number) =>
        acceleratorIndex === index ? { ...accelerator, ...changes } : accelerator,
      ),
    );
  };

  private removeAccelerator = (index: number): void => {
    this.setField(
      'acceleratorConfigs',
      (this.props.formik.values.acceleratorConfigs || []).filter(
        (_accelerator: any, acceleratorIndex: number) => acceleratorIndex !== index,
      ),
    );
  };

  private updateTag = (index: number, value: string): void => {
    const tags = this.props.formik.values.tags || [];
    this.setField(
      'tags',
      tags.map((tag: any, tagIndex: number) =>
        tagIndex === index ? (typeof tag === 'string' ? value : { ...tag, value }) : tag,
      ),
    );
  };

  private removeTag = (index: number): void => {
    this.setField(
      'tags',
      (this.props.formik.values.tags || []).filter((_tag: any, tagIndex: number) => tagIndex !== index),
    );
  };

  private removeAuthScope = (index: number): void => {
    this.setField(
      'authScopes',
      (this.props.formik.values.authScopes || []).filter((_scope: string, scopeIndex: number) => scopeIndex !== index),
    );
  };

  private setPreemptible = (preemptible: boolean): void => {
    this.setField('preemptible', preemptible);
    this.setField('automaticRestart', !preemptible);
    this.setField('onHostMaintenance', preemptible ? 'TERMINATE' : 'MIGRATE');
  };

  private booleanRadios(label: string, field: string, value: boolean): JSX.Element {
    const id = field.replace(/([A-Z])/g, '-$1').toLowerCase();
    return (
      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          <b>{label}</b>
        </div>
        <div className="col-md-2 radio">
          <label>
            <input
              checked={value === true}
              data-testid={`${id}-yes`}
              name={field}
              onChange={() => this.setField(field, true)}
              type="radio"
            />{' '}
            Yes
          </label>
        </div>
        <div className="col-md-2 radio">
          <label>
            <input
              checked={value === false}
              data-testid={`${id}-no`}
              name={field}
              onChange={() => this.setField(field, false)}
              type="radio"
            />{' '}
            No
          </label>
        </div>
      </div>
    );
  }

  public render(): JSX.Element {
    const { values } = this.props.formik;
    const errors = this.validate(values);
    const disks = getPersistentDisks(values);
    const localSsds = getLocalSsds(values);
    const accelerators = values.acceleratorConfigs || [];
    const acceleratorTypes = values.viewState?.acceleratorTypes || [];
    const tags = values.tags || [];
    const authScopes = values.authScopes || [];
    const cpuPlatforms = optionsWithPersistedValue(
      values.backingData?.filtered?.cpuPlatforms || [],
      values.minCpuPlatform,
    );
    const confidentialSettingsSupported = 'enableConfidentialCompute' in values || 'confidentialInstanceType' in values;
    const pipelineMode = isPipelineMode(values);

    return (
      <div className="container-fluid form-horizontal" data-testid="gce-advanced-settings-page">
        <div className="form-group">
          <label className="col-md-5 control-label">
            Minimum CPU Platform <HelpField id="gce.serverGroup.minCpuPlatform" />
          </label>
          <div className="col-md-6">
            <select
              className="form-control input-sm"
              data-testid="minimum-cpu-platform"
              onChange={(event) => this.setField('minCpuPlatform', event.target.value)}
              value={values.minCpuPlatform || ''}
            >
              {cpuPlatforms.map((platform) => (
                <option key={platform} value={platform}>
                  {platform}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-group">
          <div className="sm-label-left">
            <b>Storage</b>
          </div>
          <label className="col-md-5 control-label" htmlFor="gce-local-ssd-count">
            Number of Local SSD Disks
          </label>
          <div className="col-md-2">
            <input
              aria-describedby={errors.disks ? 'gce-advanced-disks-error' : undefined}
              aria-invalid={Boolean(errors.disks)}
              className="form-control input-sm"
              data-testid="local-ssd-count"
              disabled={values.viewState?.instanceTypeDetails?.storage?.localSSDSupported === false}
              id="gce-local-ssd-count"
              max={8}
              min={0}
              onChange={(event) => this.updateLocalSsdCount(Number(event.target.value))}
              type="number"
              value={localSsds.length}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="sm-label-left">
            <b>Persistent Disks</b>
          </div>
          <table className="table table-condensed packed tags">
            <thead>
              <tr>
                <th>Type</th>
                <th>Size (GB)</th>
                <th>Source image</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {disks.map((disk: any, index: number) => {
                const diskTypes = optionsWithPersistedValue(values.backingData?.persistentDiskTypes || [], disk.type);
                return (
                  <tr key={index}>
                    <td>
                      <select
                        aria-describedby={errors.disks ? 'gce-advanced-disks-error' : undefined}
                        aria-invalid={Boolean(errors.disks)}
                        aria-label={`Disk type ${index + 1}`}
                        className="form-control input-sm"
                        data-testid={`disk-type-${index}`}
                        onChange={(event) => this.updateDisk(index, { type: event.target.value })}
                        required={true}
                        value={disk.type || ''}
                      >
                        {diskTypes.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        aria-describedby={errors.disks ? 'gce-advanced-disks-error' : undefined}
                        aria-invalid={Boolean(errors.disks)}
                        aria-label={`Disk size ${index + 1}`}
                        className="form-control input-sm"
                        data-testid={`disk-size-${index}`}
                        max={pipelineMode ? undefined : 65536}
                        min={pipelineMode ? undefined : 10}
                        onChange={(event) =>
                          this.updateDisk(index, { sizeGb: parseNumericOrExpression(event.target.value) })
                        }
                        required={true}
                        type={pipelineMode ? 'text' : 'number'}
                        value={disk.sizeGb ?? ''}
                      />
                    </td>
                    <td>
                      <input
                        aria-label={`Disk source image ${index + 1}`}
                        className="form-control input-sm"
                        onChange={(event) => this.updateDisk(index, { sourceImage: event.target.value || undefined })}
                        type="text"
                        value={disk.sourceImage || ''}
                      />
                    </td>
                    <td>
                      <button
                        aria-label={`Remove disk ${index + 1}`}
                        className="btn btn-link"
                        data-testid={`remove-disk-${index}`}
                        onClick={() => this.removeDisk(index)}
                        type="button"
                      >
                        <span className="glyphicon glyphicon-trash" />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={4}>
                  <button
                    className="btn btn-block btn-sm add-new"
                    data-testid="add-disk"
                    onClick={() => this.setField('disks', [...disks, { type: 'pd-ssd', sizeGb: 10 }, ...localSsds])}
                    type="button"
                  >
                    <span className="glyphicon glyphicon-plus-sign" /> Add disk
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
          {renderValidationError(errors.disks, 'gce-advanced-disks-error')}
        </div>

        {(accelerators.length > 0 || acceleratorTypes.length > 0) && (
          <div className="form-group">
            <div className="sm-label-left">
              <b>Accelerators</b> <HelpField id="gce.serverGroup.accelerator" />
            </div>
            <table className="table table-condensed packed tags">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Count</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {accelerators.map((accelerator: any, index: number) => {
                  const selectedType = acceleratorTypes.find((type: any) => type.name === accelerator.acceleratorType);
                  const typeNames = optionsWithPersistedValue(
                    acceleratorTypes.map((type: any) => type.name),
                    accelerator.acceleratorType,
                  );
                  const counts = Array.from(
                    new Set(
                      [...(selectedType?.availableCardCounts || []), accelerator.acceleratorCount].filter(Boolean),
                    ),
                  );
                  return (
                    <tr key={index}>
                      <td>
                        <select
                          aria-describedby={errors.acceleratorConfigs ? 'gce-advanced-accelerators-error' : undefined}
                          aria-invalid={Boolean(errors.acceleratorConfigs)}
                          aria-label={`Accelerator type ${index + 1}`}
                          className="form-control input-sm"
                          data-testid={`accelerator-type-${index}`}
                          onChange={(event) => this.updateAccelerator(index, { acceleratorType: event.target.value })}
                          required={true}
                          value={accelerator.acceleratorType || ''}
                        >
                          {typeNames.map((type) => (
                            <option key={type} value={type}>
                              {acceleratorTypes.find((candidate: any) => candidate.name === type)?.description || type}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        {pipelineMode ? (
                          <input
                            aria-describedby={errors.acceleratorConfigs ? 'gce-advanced-accelerators-error' : undefined}
                            aria-invalid={Boolean(errors.acceleratorConfigs)}
                            aria-label={`Accelerator count ${index + 1}`}
                            className="form-control input-sm"
                            data-testid={`accelerator-count-${index}`}
                            onChange={(event) =>
                              this.updateAccelerator(index, {
                                acceleratorCount: parseNumericOrExpression(event.target.value),
                              })
                            }
                            required={true}
                            type="text"
                            value={accelerator.acceleratorCount ?? ''}
                          />
                        ) : counts.length ? (
                          <select
                            aria-describedby={errors.acceleratorConfigs ? 'gce-advanced-accelerators-error' : undefined}
                            aria-invalid={Boolean(errors.acceleratorConfigs)}
                            aria-label={`Accelerator count ${index + 1}`}
                            className="form-control input-sm"
                            data-testid={`accelerator-count-${index}`}
                            onChange={(event) =>
                              this.updateAccelerator(index, {
                                acceleratorCount: parseNumericOrExpression(event.target.value),
                              })
                            }
                            required={true}
                            value={accelerator.acceleratorCount ?? ''}
                          >
                            {counts.map((count) => (
                              <option key={count} value={count}>
                                {count}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <input
                            aria-describedby={errors.acceleratorConfigs ? 'gce-advanced-accelerators-error' : undefined}
                            aria-invalid={Boolean(errors.acceleratorConfigs)}
                            aria-label={`Accelerator count ${index + 1}`}
                            className="form-control input-sm"
                            data-testid={`accelerator-count-${index}`}
                            min={1}
                            onChange={(event) =>
                              this.updateAccelerator(index, {
                                acceleratorCount: parseNumericOrExpression(event.target.value),
                              })
                            }
                            required={true}
                            type="number"
                            value={accelerator.acceleratorCount ?? ''}
                          />
                        )}
                      </td>
                      <td>
                        <button
                          aria-label={`Remove accelerator ${index + 1}`}
                          className="btn btn-link"
                          data-testid={`remove-accelerator-${index}`}
                          onClick={() => this.removeAccelerator(index)}
                          type="button"
                        >
                          <span className="glyphicon glyphicon-trash" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}>
                    <button
                      className="btn btn-block btn-sm add-new"
                      data-testid="add-accelerator"
                      disabled={!acceleratorTypes.length}
                      onClick={() => {
                        const type = acceleratorTypes[0];
                        this.setField('acceleratorConfigs', [
                          ...accelerators,
                          { acceleratorType: type.name, acceleratorCount: type.availableCardCounts?.[0] || 1 },
                        ]);
                      }}
                      type="button"
                    >
                      <span className="glyphicon glyphicon-plus-sign" /> Add accelerator
                    </button>
                  </td>
                </tr>
              </tfoot>
            </table>
            {renderValidationError(errors.acceleratorConfigs, 'gce-advanced-accelerators-error')}
          </div>
        )}

        <div className="form-group">
          <label className="sm-label-left" htmlFor="gce-user-data">
            User Data <HelpField id="gce.serverGroup.userData" />
          </label>
          <textarea
            className="form-control"
            id="gce-user-data"
            onChange={(event) => this.setField('userData', event.target.value)}
            rows={3}
            value={values.userData || ''}
          />
        </div>

        <div
          aria-describedby={errors.instanceMetadata ? 'gce-advanced-instance-metadata-error' : undefined}
          aria-invalid={Boolean(errors.instanceMetadata)}
          aria-label="Custom Metadata"
          className="form-group"
          role="group"
        >
          <MapEditor
            addButtonLabel="Add New Metadata"
            allowEmpty={true}
            keyLabel="Metadata key"
            label="Custom Metadata"
            model={values.instanceMetadata || {}}
            onChange={(model) => this.setField('instanceMetadata', model)}
            valueLabel="Metadata value"
          />
          {renderValidationError(errors.instanceMetadata, 'gce-advanced-instance-metadata-error')}
        </div>
        <div
          aria-describedby={errors.labels ? 'gce-advanced-labels-error' : undefined}
          aria-invalid={Boolean(errors.labels)}
          aria-label="Labels"
          className="form-group"
          role="group"
        >
          <MapEditor
            addButtonLabel="Add New Label"
            allowEmpty={true}
            keyLabel="Label key"
            label="Labels"
            model={values.labels || {}}
            onChange={(model) => this.setField('labels', model)}
            valueLabel="Label value"
          />
          {renderValidationError(errors.labels, 'gce-advanced-labels-error')}
        </div>
        <div
          aria-describedby={errors.resourceManagerTags ? 'gce-advanced-resource-manager-tags-error' : undefined}
          aria-invalid={Boolean(errors.resourceManagerTags)}
          aria-label="Resource Manager Tags"
          className="form-group"
          role="group"
        >
          <MapEditor
            addButtonLabel="Add New Resource Manager Tag"
            allowEmpty={false}
            keyLabel="Tag key"
            label="Resource Manager Tags"
            model={values.resourceManagerTags || {}}
            onChange={(model) => this.setField('resourceManagerTags', model)}
            valueLabel="Tag value"
          />
          {renderValidationError(errors.resourceManagerTags, 'gce-advanced-resource-manager-tags-error')}
        </div>
        <div className="form-group">
          <div className="sm-label-left">
            <b>Network Tags</b>
          </div>
          <table className="table table-condensed packed tags">
            <tbody>
              {tags.map((tag: any, index: number) => (
                <tr key={index}>
                  <td>
                    <input
                      aria-describedby={errors.tags ? 'gce-advanced-network-tags-error' : undefined}
                      aria-invalid={Boolean(errors.tags)}
                      aria-label={`Network tag ${index + 1}`}
                      className="form-control input-sm"
                      onChange={(event) => this.updateTag(index, event.target.value)}
                      required={true}
                      value={typeof tag === 'string' ? tag : tag.value || ''}
                    />
                  </td>
                  <td>
                    <button
                      aria-label={`Remove network tag ${index + 1}`}
                      className="btn btn-link"
                      data-testid={`remove-network-tag-${index}`}
                      onClick={() => this.removeTag(index)}
                      type="button"
                    >
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>
                  <button
                    className="btn btn-block btn-sm add-new"
                    data-testid="add-network-tag"
                    onClick={() => this.setField('tags', [...tags, { value: '' }])}
                    type="button"
                  >
                    <span className="glyphicon glyphicon-plus-sign" /> Add network tag
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
          {renderValidationError(errors.tags, 'gce-advanced-network-tags-error')}
        </div>

        <fieldset className="form-group">
          <legend className="sm-label-left">
            Shielded VM <HelpField id="gce.serverGroup.shieldedInstanceConfig" />
          </legend>
          <div className="col-md-9 checkbox">
            <label>
              <input
                checked={Boolean(values.enableSecureBoot)}
                onChange={(event) => this.setField('enableSecureBoot', event.target.checked)}
                type="checkbox"
              />{' '}
              Turn on Secure Boot
            </label>
          </div>
          <div className="col-md-9 checkbox">
            <label>
              <input
                checked={Boolean(values.enableVtpm)}
                data-testid="enable-vtpm"
                onChange={(event) => {
                  this.setField('enableVtpm', event.target.checked);
                  if (!event.target.checked) {
                    this.setField('enableIntegrityMonitoring', false);
                  }
                }}
                type="checkbox"
              />{' '}
              Turn on vTPM
            </label>
          </div>
          <div className="col-md-9 checkbox">
            <label>
              <input
                checked={Boolean(values.enableIntegrityMonitoring)}
                disabled={!values.enableVtpm}
                onChange={(event) => this.setField('enableIntegrityMonitoring', event.target.checked)}
                type="checkbox"
              />{' '}
              Turn on Integrity Monitoring
            </label>
          </div>
        </fieldset>

        {confidentialSettingsSupported && (
          <fieldset className="form-group">
            <legend className="sm-label-left">Confidential VM</legend>
            {'enableConfidentialCompute' in values && (
              <div className="col-md-9 checkbox" data-testid="enable-confidential-compute">
                <label>
                  <input
                    checked={Boolean(values.enableConfidentialCompute)}
                    onChange={(event) => this.setField('enableConfidentialCompute', event.target.checked)}
                    type="checkbox"
                  />{' '}
                  Enable Confidential Compute
                </label>
              </div>
            )}
            {'confidentialInstanceType' in values && (
              <div className="col-md-9">
                <label htmlFor="gce-confidential-instance-type">Confidential instance type</label>
                <select
                  aria-describedby={
                    errors.confidentialInstanceType ? 'gce-advanced-confidential-instance-type-error' : undefined
                  }
                  aria-invalid={Boolean(errors.confidentialInstanceType)}
                  className="form-control input-sm"
                  id="gce-confidential-instance-type"
                  onChange={(event) => this.setField('confidentialInstanceType', event.target.value)}
                  required={Boolean(values.enableConfidentialCompute)}
                  value={values.confidentialInstanceType || ''}
                >
                  <option value="">Select...</option>
                  {optionsWithPersistedValue(
                    values.backingData?.confidentialInstanceTypes || [],
                    values.confidentialInstanceType,
                  ).map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
                {renderValidationError(
                  errors.confidentialInstanceType,
                  'gce-advanced-confidential-instance-type-error',
                )}
              </div>
            )}
          </fieldset>
        )}

        <div className="form-group">
          <div className="col-md-5 sm-label-right">
            <b>Preemptibility</b>
          </div>
          <div className="col-md-2 radio">
            <label>
              <input
                checked={!values.preemptible}
                data-testid="preemptible-off"
                name="preemptible"
                onChange={() => this.setPreemptible(false)}
                type="radio"
              />{' '}
              Off
            </label>
          </div>
          <div className="col-md-2 radio">
            <label>
              <input
                checked={Boolean(values.preemptible)}
                data-testid="preemptible-on"
                name="preemptible"
                onChange={() => this.setPreemptible(true)}
                type="radio"
              />{' '}
              On
            </label>
          </div>
        </div>

        {this.booleanRadios('Automatic Restart', 'automaticRestart', Boolean(values.automaticRestart))}
        <div className="form-group">
          <label className="col-md-5 control-label">On Host Maintenance</label>
          <div className="col-md-4">
            <select
              className="form-control input-sm"
              onChange={(event) => this.setField('onHostMaintenance', event.target.value)}
              value={values.onHostMaintenance || ''}
            >
              <option value="MIGRATE">Migrate</option>
              <option value="TERMINATE">Terminate</option>
            </select>
          </div>
        </div>
        {this.booleanRadios(
          'Associate Public IP Address',
          'associatePublicIpAddress',
          values.associatePublicIpAddress !== false,
        )}
        {this.booleanRadios('Can IP Forward', 'canIpForward', Boolean(values.canIpForward))}

        <div className="form-group" data-testid="service-account">
          <label className="col-md-5 control-label" htmlFor="gce-service-account">
            Service Account
          </label>
          <div className="col-md-6">
            <input
              className="form-control input-sm"
              id="gce-service-account"
              onChange={(event) => this.setField('serviceAccountEmail', event.target.value)}
              type="text"
              value={values.serviceAccountEmail || ''}
            />
          </div>
        </div>

        <div className="form-group">
          <div className="sm-label-left">
            <b>Auth Scopes</b>
          </div>
          <table className="table table-condensed packed tags">
            <tbody>
              {authScopes.map((scope: string, index: number) => (
                <tr key={index} data-testid={`auth-scope-${index}`}>
                  <td>
                    <input
                      aria-describedby={errors.authScopes ? 'gce-advanced-auth-scopes-error' : undefined}
                      aria-invalid={Boolean(errors.authScopes)}
                      aria-label={`Auth scope ${index + 1}`}
                      className="form-control input-sm"
                      list="gce-auth-scopes"
                      onChange={(event) =>
                        this.setField(
                          'authScopes',
                          authScopes.map((candidate: string, scopeIndex: number) =>
                            scopeIndex === index ? event.target.value : candidate,
                          ),
                        )
                      }
                      required={true}
                      value={scope}
                    />
                  </td>
                  <td>
                    <button
                      aria-label={`Remove auth scope ${index + 1}`}
                      className="btn btn-link"
                      data-testid={`remove-auth-scope-${index}`}
                      onClick={() => this.removeAuthScope(index)}
                      type="button"
                    >
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>
                  <button
                    className="btn btn-block btn-sm add-new"
                    data-testid="add-auth-scope"
                    onClick={() => this.setField('authScopes', [...authScopes, ''])}
                    type="button"
                  >
                    <span className="glyphicon glyphicon-plus-sign" /> Add auth scope
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
          <datalist id="gce-auth-scopes">
            {(values.backingData?.authScopes || []).map((scope: string) => (
              <option key={scope} value={scope} />
            ))}
          </datalist>
          {renderValidationError(errors.authScopes, 'gce-advanced-auth-scopes-error')}
        </div>
      </div>
    );
  }
}
