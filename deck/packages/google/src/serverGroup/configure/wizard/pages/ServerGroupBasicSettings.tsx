import React from 'react';

import { DeploymentStrategySelector, TaskReason } from '@spinnaker/core';

import { preservePersistedReference, validateGceServerGroupCommand } from '../GceServerGroupWizard.helpers';
import type {
  GceCommandHandlerName,
  IGceServerGroupCommand,
  IGceServerGroupCommandValidationErrors,
} from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';
import { GceImageReader } from '../../../../image';

interface ISelectOption {
  label: string;
  unavailable?: boolean;
  value: string;
}

export class ServerGroupBasicSettings extends GceServerGroupWizardPage {
  public validate(values: IGceServerGroupCommand): IGceServerGroupCommandValidationErrors {
    const sharedErrors = validateGceServerGroupCommand(values);
    const errors: IGceServerGroupCommandValidationErrors = {};
    if (sharedErrors.credentials) errors.credentials = sharedErrors.credentials;
    if (sharedErrors.region) errors.region = sharedErrors.region;
    if (sharedErrors.zone) errors.zone = sharedErrors.zone;
    if (sharedErrors.stack) errors.stack = sharedErrors.stack;
    if (sharedErrors.freeFormDetails) errors.freeFormDetails = sharedErrors.freeFormDetails;
    const stackPattern = values.viewState.templatingEnabled ? /^([a-zA-Z0-9]*(\${.+})*)*$/ : /^[a-zA-Z0-9]*$/;
    const detailPattern = values.viewState.templatingEnabled ? /^([a-zA-Z0-9-]*(\${.+})*)*$/ : /^[a-zA-Z0-9-]*$/;

    if (values.stack && !stackPattern.test(values.stack)) {
      errors.stack = 'Stack can only contain letters and numbers.';
    }
    if (values.freeFormDetails && !detailPattern.test(values.freeFormDetails)) {
      errors.freeFormDetails = 'Detail can only contain letters, numbers, and dashes.';
    }

    return errors;
  }

  private parentChanged = (
    field: string,
    value: string | boolean,
    handler: GceCommandHandlerName,
  ): Promise<IGceServerGroupCommand | undefined> => {
    const command = { ...this.props.formik.values, [field]: value };
    return this.runLatestCommandRequest(
      command,
      async (latestCommand) => (await this.adapter.applyCommandHandler(latestCommand, handler)).command,
    );
  };

  private accountChanged = (account: string): Promise<IGceServerGroupCommand | undefined> => {
    const command = { ...this.props.formik.values, credentials: account };
    return this.runLatestCommandRequest(command, async (latestCommand) => {
      const images = await GceImageReader.findImages({ account, provider: latestCommand.selectedProvider, q: '*' });
      const commandWithImages = {
        ...latestCommand,
        backingData: { ...latestCommand.backingData, allImages: images },
      };
      return (await this.adapter.applyCommandHandler(commandWithImages, 'credentialsChanged')).command;
    });
  };

  private renderSelect(
    label: string,
    field: string,
    options: ISelectOption[],
    onChange?: (value: string) => void,
    error?: string,
  ) {
    const id = `gce-server-group-${field}`;
    const errorId = `${id}-error`;
    const fieldValue = this.props.formik.values[field];
    const value = field === 'regional' ? (fieldValue ? 'regional' : 'zonal') : fieldValue || '';
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor={id}>
          {label}
        </label>
        <div className="col-md-7">
          <select
            aria-describedby={error ? errorId : undefined}
            aria-invalid={Boolean(error)}
            aria-label={label}
            className="form-control input-sm"
            id={id}
            onChange={(event) =>
              onChange ? onChange(event.target.value) : this.props.formik.setFieldValue(field, event.target.value)
            }
            value={value}
          >
            <option value="">Select...</option>
            {options.map((option) => (
              <option disabled={option.unavailable} key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {error && (
            <span className="help-block" id={errorId} role="alert">
              {error}
            </span>
          )}
        </div>
      </div>
    );
  }

  private renderTextField(label: string, field: 'stack' | 'freeFormDetails', error?: string) {
    const id = `gce-server-group-${field}`;
    const errorId = `${id}-error`;
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor={id}>
          {label}
        </label>
        <div className="col-md-7">
          <input
            aria-describedby={error ? errorId : undefined}
            aria-invalid={Boolean(error)}
            aria-label={label}
            className="form-control input-sm"
            id={id}
            onChange={(event) => this.props.formik.setFieldValue(field, event.target.value)}
            type="text"
            value={this.props.formik.values[field] || ''}
          />
          {error && (
            <span className="help-block" id={errorId} role="alert">
              {error}
            </span>
          )}
        </div>
      </div>
    );
  }

  private strategyChanged = (command: IGceServerGroupCommand, strategy: any): void => {
    command.onStrategyChange?.(command, strategy);
    this.props.formik.setValues({ ...command, strategy: strategy.key });
  };

  private strategyFieldChanged = (field: string, value: any): void => {
    this.props.formik.setFieldValue(field, value);
  };

  public render(): JSX.Element {
    const { values } = this.props.formik;
    const filtered = values.backingData?.filtered || {};
    const accounts = referenceOptions(values.backingData?.accounts, values.credentials, ['name']);
    const regions = referenceOptions(filtered.regions, values.region, ['name']);
    const zones = referenceOptions(filtered.zones, values.zone, ['name']);
    const networks = referenceOptions(filtered.networks, values.network, ['id', 'name']);
    const subnets = referenceOptions(filtered.subnets, values.subnet, ['id', 'name']);
    const errors = this.validate(values);

    return (
      <div className="container-fluid form-horizontal">
        {this.renderSelect('Account', 'credentials', accounts, this.accountChanged, errors.credentials)}
        {this.renderSelect(
          'Region',
          'region',
          regions,
          (region) => this.parentChanged('region', region, 'regionChanged'),
          errors.region,
        )}
        {this.renderSelect(
          'Location mode',
          'regional',
          [
            { label: 'Zonal', value: 'zonal' },
            { label: 'Regional', value: 'regional' },
          ],
          (mode) => this.parentChanged('regional', mode === 'regional', 'regionalChanged'),
        )}
        {!values.regional &&
          this.renderSelect(
            'Zone',
            'zone',
            zones,
            (zone) => this.parentChanged('zone', zone, 'zoneChanged'),
            errors.zone,
          )}
        {this.renderSelect('Network', 'network', networks, (network) =>
          this.parentChanged('network', network, 'networkChanged'),
        )}
        {this.renderSelect('Subnet', 'subnet', subnets)}
        {this.renderTextField('Stack', 'stack', errors.stack)}
        {this.renderTextField('Detail', 'freeFormDetails', errors.freeFormDetails)}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Traffic</div>
          <div className="col-md-7 checkbox">
            <label>
              <input
                aria-label="Send client requests to new instances"
                checked={values.enableTraffic !== false}
                onChange={(event) => this.props.formik.setFieldValue('enableTraffic', event.target.checked)}
                type="checkbox"
              />{' '}
              Send client requests to new instances
            </label>
          </div>
        </div>
        {!values.viewState.disableStrategySelection && values.selectedProvider && (
          <div aria-label="Deployment strategy" role="group">
            <DeploymentStrategySelector
              command={values as any}
              onFieldChange={this.strategyFieldChanged}
              onStrategyChange={this.strategyChanged as any}
            />
          </div>
        )}
        <TaskReason
          reason={values.reason || ''}
          onChange={(reason) => this.props.formik.setFieldValue('reason', reason)}
        />
      </div>
    );
  }
}

function referenceOptions(
  rawOptions: readonly any[] | null | undefined,
  persistedValue: string | null | undefined,
  objectKeys: string[],
): ISelectOption[] {
  const values = (rawOptions || []).map((option) => optionValue(option, objectKeys)).filter(Boolean);
  return preservePersistedReference(
    values,
    persistedValue,
    (value) => value,
    (value) => value,
  ).map(({ unresolved, value }) => ({
    label: unresolved ? `${value} (unavailable)` : value,
    unavailable: unresolved,
    value,
  }));
}

function optionValue(option: any, objectKeys: string[]): string {
  if (typeof option === 'string') {
    return option;
  }
  return objectKeys.map((key) => option?.[key]).find(Boolean) || '';
}
