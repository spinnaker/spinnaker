import React from 'react';

import { HelpField } from '@spinnaker/core';

import type {
  IGceServerGroupCommand,
  IGceServerGroupCommandValidationErrors,
  IGceServerGroupWizardPageProps,
} from '../GceServerGroupWizard.types';
import { GceServerGroupWizardPage } from '../GceServerGroupWizardPage';

interface ILoadBalancerOption {
  name: string;
  region?: string;
}

interface IBackendServiceData {
  name: string;
  portName?: string;
}

interface ILoadBalancerData {
  backendServices?: Array<string | Partial<IBackendServiceData>>;
  listeners?: Array<{ name?: string }>;
  loadBalancerType?: string;
  name?: string;
}

interface INamedPort {
  name: string;
  port: number | string;
}

interface ILoadBalancingPolicy {
  [key: string]: any;
  balancingMode?: string;
  capacityScaler?: number | string;
  maxConnectionsPerInstance?: number | string;
  maxRatePerInstance?: number | string;
  maxUtilization?: number | string;
  namedPorts?: INamedPort[];
}

interface ILoadBalancingPolicyErrors {
  balancingMode?: string;
  capacityScaler?: string;
  maxConnectionsPerInstance?: string;
  maxRatePerInstance?: string;
  maxUtilization?: string;
  namedPorts?: Array<{ name?: string; port?: string }>;
}

interface ILoadBalancerMetadataReference {
  key: 'global-load-balancer-names' | 'load-balancer-names';
  names: string[];
}

interface IStringReference {
  unavailable: boolean;
  value: string;
}

type PreservedField =
  | 'backendServiceMetadata'
  | 'backendServices'
  | 'loadBalancerMetadata'
  | 'loadBalancers'
  | 'loadBalancingPolicy';

const HTTP_BALANCING_MODES = ['RATE', 'UTILIZATION'];
const CONNECTION_BALANCING_MODES = ['CONNECTION', 'UTILIZATION'];
const MODE_LIMIT_FIELDS = ['maxConnectionsPerInstance', 'maxRatePerInstance', 'maxUtilization'];
const DEFAULT_LOAD_BALANCING_POLICY = {
  balancingMode: 'UTILIZATION',
  capacityScaler: 1,
  maxUtilization: 0.8,
  namedPorts: [{ name: 'http', port: 80 }],
};

function policyErrorId(field: string): string {
  return `gce-load-balancing-policy-${field.replace(/([A-Z])/g, '-$1').toLowerCase()}-error`;
}

function renderPolicyError(error: string | undefined, id: string): React.ReactNode {
  return (
    error && (
      <span className="help-block" id={id} role="alert">
        {error}
      </span>
    )
  );
}

export class GceServerGroupLoadBalancers extends GceServerGroupWizardPage<IGceServerGroupWizardPageProps> {
  public validate(values: IGceServerGroupCommand): IGceServerGroupCommandValidationErrors {
    const errors = super.validate(values) as IGceServerGroupCommandValidationErrors & {
      loadBalancingPolicy?: ILoadBalancingPolicyErrors;
    };
    const policy = values.loadBalancingPolicy as ILoadBalancingPolicy | undefined;
    if (!policy || !uniqueStrings(values.loadBalancers).length) {
      return errors;
    }

    const policyErrors: ILoadBalancingPolicyErrors = {};
    const balancingModes = getBalancingModes(values);
    if (!policy.balancingMode || !balancingModes.includes(policy.balancingMode)) {
      policyErrors.balancingMode = 'Select a balancing mode supported by the selected load balancers.';
    }
    if (!isValidBoundedValue(policy.capacityScaler, values, 0, 1)) {
      policyErrors.capacityScaler = 'Capacity must be between 0 and 100%.';
    }

    const namedPortErrors = (policy.namedPorts || []).map(({ name, port }) => {
      const namedPortError: { name?: string; port?: string } = {};
      if (!name?.trim()) {
        namedPortError.name = 'Port name required.';
      }
      if (!isValidInteger(port, values, 1, 65535)) {
        namedPortError.port = 'Port must be an integer between 1 and 65535.';
      }
      return namedPortError;
    });
    if (namedPortErrors.some((namedPortError) => Object.keys(namedPortError).length)) {
      policyErrors.namedPorts = namedPortErrors;
    }

    if (policy.balancingMode === 'RATE' && !isValidMinimum(policy.maxRatePerInstance, values, 0)) {
      policyErrors.maxRatePerInstance = 'Max rate must be a finite number greater than or equal to zero.';
    }
    if (policy.balancingMode === 'CONNECTION' && !isValidMinimum(policy.maxConnectionsPerInstance, values, 0)) {
      policyErrors.maxConnectionsPerInstance = 'Max connections must be a finite number greater than or equal to zero.';
    }
    if (policy.balancingMode === 'UTILIZATION' && !isValidBoundedValue(policy.maxUtilization, values, 0, 1)) {
      policyErrors.maxUtilization = 'Max utilization must be between 0 and 100%.';
    }

    if (Object.keys(policyErrors).length) {
      errors.loadBalancingPolicy = policyErrors;
    }
    return errors;
  }

  public render(): React.ReactElement {
    const values = this.props.formik.values;
    const selectedLoadBalancers = uniqueStrings(values.loadBalancers);
    const selectedReferences = preserveReferences(getAvailableLoadBalancers(values), selectedLoadBalancers);
    const policy = (values.loadBalancingPolicy || {}) as ILoadBalancingPolicy;
    const policyErrors = (this.validate(values) as IGceServerGroupCommandValidationErrors & {
      loadBalancingPolicy?: ILoadBalancingPolicyErrors;
    }).loadBalancingPolicy || { namedPorts: [] };
    const balancingModes = getBalancingModes(values);

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <label className="col-md-3 sm-label-right" htmlFor="gce-server-group-load-balancers">
            Load Balancers
          </label>
          <div className="col-md-7">
            <select
              aria-label="Load balancers"
              className="form-control input-sm"
              id="gce-server-group-load-balancers"
              multiple={true}
              onChange={this.handleLoadBalancersChanged}
              value={selectedLoadBalancers}
            >
              {selectedReferences.map(({ name, unavailable }) => (
                <option key={name} value={name}>
                  {name}
                  {unavailable ? ' (unavailable)' : ''}
                </option>
              ))}
            </select>
          </div>
          <div className="col-md-1">
            <button
              aria-label="Refresh load balancers"
              className="btn btn-default btn-sm"
              onClick={this.handleRefresh}
              type="button"
            >
              <span className="glyphicon glyphicon-refresh" />
            </button>
          </div>
        </div>

        {selectedLoadBalancers.map((loadBalancerName) => this.renderBackendServiceSelector(values, loadBalancerName))}

        {selectedLoadBalancers.length > 0 && balancingModes.length > 0 && (
          <>
            <div className="form-group">
              <label className="col-md-3 sm-label-right" htmlFor="gce-balancing-mode">
                Balancing mode
              </label>
              <div className="col-md-4">
                <select
                  aria-describedby={policyErrors.balancingMode ? policyErrorId('balancingMode') : undefined}
                  aria-invalid={Boolean(policyErrors.balancingMode)}
                  aria-label="Balancing mode"
                  className="form-control input-sm"
                  id="gce-balancing-mode"
                  onChange={this.handleBalancingModeChanged}
                  required={true}
                  value={policy.balancingMode || ''}
                >
                  {!policy.balancingMode && <option value="">Select...</option>}
                  {balancingModes.map((mode) => (
                    <option key={mode} value={mode}>
                      {mode}
                    </option>
                  ))}
                </select>
                {renderPolicyError(policyErrors.balancingMode, policyErrorId('balancingMode'))}
              </div>
            </div>

            {this.renderNamedPorts(values, policy, policyErrors.namedPorts || [])}
            {this.renderPercentageInput(
              'Capacity scaler',
              'capacityScaler',
              policy.capacityScaler,
              values,
              policyErrors.capacityScaler,
            )}
            {policy.balancingMode === 'UTILIZATION' &&
              this.renderPercentageInput(
                'Max utilization',
                'maxUtilization',
                policy.maxUtilization,
                values,
                policyErrors.maxUtilization,
              )}
            {policy.balancingMode === 'RATE' &&
              this.renderNumberInput(
                'Max rate per instance',
                'maxRatePerInstance',
                policy.maxRatePerInstance,
                values,
                policyErrors.maxRatePerInstance,
              )}
            {policy.balancingMode === 'CONNECTION' &&
              this.renderNumberInput(
                'Max connections per instance',
                'maxConnectionsPerInstance',
                policy.maxConnectionsPerInstance,
                values,
                policyErrors.maxConnectionsPerInstance,
              )}
          </>
        )}
      </div>
    );
  }

  private renderBackendServiceSelector(
    values: IGceServerGroupCommand,
    loadBalancerName: string,
  ): React.ReactElement | null {
    const availableBackendServices = getBackendServiceData(values, loadBalancerName).map(({ name }) => name);
    const selectedBackendServices = uniqueStrings(values.backendServices?.[loadBalancerName]);
    if (!availableBackendServices.length && !selectedBackendServices.length) {
      return null;
    }
    const references = preserveStringReferences(availableBackendServices, selectedBackendServices);

    return (
      <div className="form-group" key={loadBalancerName}>
        <label className="col-md-3 sm-label-right" htmlFor={`gce-backend-services-${loadBalancerName}`}>
          Backend services for {loadBalancerName}
        </label>
        <div className="col-md-7">
          <select
            aria-label={`Backend services for ${loadBalancerName}`}
            className="form-control input-sm"
            id={`gce-backend-services-${loadBalancerName}`}
            multiple={true}
            onChange={(event) => this.handleBackendServicesChanged(loadBalancerName, event)}
            value={selectedBackendServices}
          >
            {references.map(({ unavailable, value }) => (
              <option key={value} value={value}>
                {value}
                {unavailable ? ' (unavailable)' : ''}
              </option>
            ))}
          </select>
        </div>
      </div>
    );
  }

  private renderNamedPorts(
    values: IGceServerGroupCommand,
    policy: ILoadBalancingPolicy,
    errors: Array<{ name?: string; port?: string }>,
  ): React.ReactElement {
    const availableNames = getNamedPortNames(values);
    const namedPorts = policy.namedPorts || [];
    return (
      <>
        {namedPorts.map((namedPort, index) => {
          const references = preserveStringReferences(availableNames, namedPort.name ? [namedPort.name] : []);
          const nameErrorId = policyErrorId(`namedPortName-${index}`);
          const portErrorId = policyErrorId(`namedPortPort-${index}`);
          return (
            <div className="form-group" key={index}>
              <label className="col-md-3 sm-label-right" htmlFor={`gce-named-port-name-${index}`}>
                Named port {index + 1}
              </label>
              <div className="col-md-3">
                <select
                  aria-describedby={errors[index]?.name ? nameErrorId : undefined}
                  aria-invalid={Boolean(errors[index]?.name)}
                  aria-label={`Named port name ${index + 1}`}
                  className="form-control input-sm"
                  id={`gce-named-port-name-${index}`}
                  onChange={(event) => this.handleNamedPortChanged(index, 'name', event.target.value)}
                  required={true}
                  value={namedPort.name}
                >
                  {!namedPort.name && <option value="">Select...</option>}
                  {references.map(({ unavailable, value }) => (
                    <option key={value} value={value}>
                      {value}
                      {unavailable ? ' (unavailable)' : ''}
                    </option>
                  ))}
                </select>
                {renderPolicyError(errors[index]?.name, nameErrorId)}
              </div>
              <div className="col-md-2">
                <input
                  aria-describedby={errors[index]?.port ? portErrorId : undefined}
                  aria-invalid={Boolean(errors[index]?.port)}
                  aria-label={`Named port number ${index + 1}`}
                  className="form-control input-sm"
                  onChange={(event) =>
                    this.handleNamedPortChanged(index, 'port', parseNumericValue(event.target.value))
                  }
                  required={true}
                  type={isPipelineMode(values) ? 'text' : 'number'}
                  value={namedPort.port}
                />
                {renderPolicyError(errors[index]?.port, portErrorId)}
              </div>
              <div className="col-md-1">
                <button
                  aria-label={`Remove named port ${index + 1}`}
                  className="btn btn-default btn-sm"
                  onClick={() => this.handleRemoveNamedPort(index)}
                  type="button"
                >
                  <span className="glyphicon glyphicon-trash" />
                </button>
              </div>
            </div>
          );
        })}
        <div className="form-group">
          <div className="col-md-offset-3 col-md-4">
            <button
              aria-label="Add named port"
              className="btn btn-default btn-sm"
              onClick={this.handleAddNamedPort}
              type="button"
            >
              Add named port
            </button>
          </div>
        </div>
      </>
    );
  }

  private renderPercentageInput(
    label: string,
    field: 'capacityScaler' | 'maxUtilization',
    value: number | string | undefined,
    values: IGceServerGroupCommand,
    error?: string,
  ): React.ReactElement {
    const errorId = policyErrorId(field);
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor={`gce-${field}`}>
          {label}
          <HelpField id={`gce.serverGroup.loadBalancingPolicy.${field}`} />
        </label>
        <div className="col-md-3">
          <div className="input-group">
            <input
              aria-describedby={error ? errorId : undefined}
              aria-invalid={Boolean(error)}
              aria-label={label}
              className="form-control input-sm"
              id={`gce-${field}`}
              onChange={(event) => this.handlePolicyFieldChanged(field, parsePercentageValue(event.target.value))}
              required={true}
              type={isPipelineMode(values) ? 'text' : 'number'}
              value={toPercentageValue(value)}
            />
            <span className="input-group-addon">%</span>
          </div>
          {renderPolicyError(error, errorId)}
        </div>
      </div>
    );
  }

  private renderNumberInput(
    label: string,
    field: 'maxConnectionsPerInstance' | 'maxRatePerInstance',
    value: number | string | undefined,
    values: IGceServerGroupCommand,
    error?: string,
  ): React.ReactElement {
    const errorId = policyErrorId(field);
    return (
      <div className="form-group">
        <label className="col-md-3 sm-label-right" htmlFor={`gce-${field}`}>
          {label}
          <HelpField id={`gce.serverGroup.loadBalancingPolicy.${field}`} />
        </label>
        <div className="col-md-3">
          <input
            aria-describedby={error ? errorId : undefined}
            aria-invalid={Boolean(error)}
            aria-label={label}
            className="form-control input-sm"
            id={`gce-${field}`}
            onChange={(event) => this.handlePolicyFieldChanged(field, parseNumericValue(event.target.value))}
            required={true}
            type={isPipelineMode(values) ? 'text' : 'number'}
            value={value === undefined ? '' : value}
          />
          {renderPolicyError(error, errorId)}
        </div>
      </div>
    );
  }

  private handleLoadBalancersChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const values = this.props.formik.values;
    const loadBalancers = uniqueStrings(Array.from(event.target.selectedOptions).map(({ value }) => value));
    const previouslySelected = new Set(uniqueStrings(values.loadBalancers));
    const backendServices = Object.entries(values.backendServices || {}).reduce<Record<string, string[]>>(
      (mappings, [loadBalancerName, services]) => {
        if (loadBalancers.includes(loadBalancerName) || !previouslySelected.has(loadBalancerName)) {
          mappings[loadBalancerName] = uniqueStrings(services);
        }
        return mappings;
      },
      {},
    );
    const nextCommand: IGceServerGroupCommand = {
      ...values,
      loadBalancers,
      loadBalancerMetadata: reconcileLoadBalancerMetadata(values, loadBalancers),
      backendServices,
      backendServiceMetadata: uniqueStrings(Object.values(backendServices).flat()),
    };
    nextCommand.loadBalancingPolicy = reconcileLoadBalancingPolicy(nextCommand);
    void this.applyPageUpdate(nextCommand, ['loadBalancers', 'loadBalancerMetadata', 'loadBalancingPolicy'], true);
  };

  private handleBackendServicesChanged = (
    loadBalancerName: string,
    event: React.ChangeEvent<HTMLSelectElement>,
  ): void => {
    const values = this.props.formik.values;
    const backendServices = {
      ...(values.backendServices || {}),
      [loadBalancerName]: uniqueStrings(Array.from(event.target.selectedOptions).map(({ value }) => value)),
    };
    const nextCommand = {
      ...values,
      backendServices,
      backendServiceMetadata: uniqueStrings(Object.values(backendServices).flat()),
    };
    void this.applyPageUpdate(nextCommand, ['backendServices', 'backendServiceMetadata']);
  };

  private handleBalancingModeChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const policy = { ...((this.props.formik.values.loadBalancingPolicy || {}) as ILoadBalancingPolicy) };
    MODE_LIMIT_FIELDS.forEach((field) => delete policy[field]);
    policy.balancingMode = event.target.value;
    void this.updatePolicy(policy);
  };

  private handleNamedPortChanged = (index: number, field: keyof INamedPort, value: number | string): void => {
    const policy = { ...((this.props.formik.values.loadBalancingPolicy || {}) as ILoadBalancingPolicy) };
    policy.namedPorts = (policy.namedPorts || []).map((namedPort, namedPortIndex) =>
      namedPortIndex === index ? { ...namedPort, [field]: value } : namedPort,
    );
    void this.updatePolicy(policy);
  };

  private handleAddNamedPort = (): void => {
    const policy = { ...((this.props.formik.values.loadBalancingPolicy || {}) as ILoadBalancingPolicy) };
    policy.namedPorts = [...(policy.namedPorts || []), { name: '', port: 80 }];
    void this.updatePolicy(policy);
  };

  private handleRemoveNamedPort = (index: number): void => {
    const policy = { ...((this.props.formik.values.loadBalancingPolicy || {}) as ILoadBalancingPolicy) };
    policy.namedPorts = (policy.namedPorts || []).filter((_namedPort, namedPortIndex) => namedPortIndex !== index);
    void this.updatePolicy(policy);
  };

  private handlePolicyFieldChanged = (field: keyof ILoadBalancingPolicy, value: number | string): void => {
    const policy = {
      ...((this.props.formik.values.loadBalancingPolicy || {}) as ILoadBalancingPolicy),
      [field]: value,
    };
    void this.updatePolicy(policy);
  };

  private updatePolicy(policy: ILoadBalancingPolicy): Promise<IGceServerGroupCommand | undefined> {
    const values = this.props.formik.values;
    return this.applyPageUpdate({ ...values, loadBalancingPolicy: policy }, [
      'backendServices',
      'backendServiceMetadata',
      'loadBalancers',
      'loadBalancerMetadata',
      'loadBalancingPolicy',
    ]);
  }

  private applyPageUpdate(
    nextCommand: IGceServerGroupCommand,
    preservedFields: PreservedField[],
    mergeConfiguredBackendServices = false,
  ): Promise<IGceServerGroupCommand | undefined> {
    return this.runLatestCommandRequest(nextCommand, async (latestCommand) => {
      const loadBalancerIndex = getLoadBalancerIndex(latestCommand);
      const configurationCommand = {
        ...latestCommand,
        loadBalancers: uniqueStrings(latestCommand.loadBalancers).filter((name) => Boolean(loadBalancerIndex[name])),
      };
      const update = await this.adapter.applyConfigurationUpdate(configurationCommand, 'configureLoadBalancerOptions');
      const command: IGceServerGroupCommand = {
        ...update.command,
        loadBalancers: latestCommand.loadBalancers,
        viewState: {
          ...update.command.viewState,
          dirty: update.result.dirty,
        },
      };
      if (mergeConfiguredBackendServices) {
        command.backendServices = {
          ...(latestCommand.backendServices || {}),
          ...(update.command.backendServices || {}),
        };
        command.backendServiceMetadata = uniqueStrings(Object.values(command.backendServices).flat());
      }
      preservedFields.forEach((field) => {
        command[field] = latestCommand[field];
      });
      return command;
    });
  }

  private handleRefresh = (): void => {
    const selectedLoadBalancers = uniqueStrings(this.props.formik.values.loadBalancers);
    void this.runLatestCommandRequest(this.props.formik.values, async (latestCommand) => {
      const update = await this.adapter.applyConfigurationRefresh(latestCommand, 'refreshLoadBalancers');
      return {
        ...update.command,
        loadBalancers: selectedLoadBalancers,
        viewState: {
          ...update.command.viewState,
          dirty: update.result.dirty,
        },
      };
    });
  };
}

function getAvailableLoadBalancers(command: IGceServerGroupCommand): ILoadBalancerOption[] {
  const rawLoadBalancers = command.backingData?.loadBalancers;
  if (Array.isArray(rawLoadBalancers)) {
    return uniqueLoadBalancers(
      rawLoadBalancers
        .flatMap((provider: any) => (Array.isArray(provider?.accounts) ? provider.accounts : []))
        .filter((account: any) => account?.name === command.credentials)
        .flatMap((account: any) => (Array.isArray(account?.regions) ? account.regions : []))
        .flatMap((region: any) => (Array.isArray(region?.loadBalancers) ? region.loadBalancers : []))
        .filter((loadBalancer: any) => isLoadBalancerInRegion(loadBalancer, command))
        .map(toLoadBalancerOption)
        .filter((loadBalancer): loadBalancer is ILoadBalancerOption => Boolean(loadBalancer)),
    );
  }

  const filteredLoadBalancers = command.backingData?.filtered?.loadBalancers;
  if (!Array.isArray(filteredLoadBalancers)) {
    return [];
  }
  return uniqueLoadBalancers(
    filteredLoadBalancers
      .map(toLoadBalancerOption)
      .filter((loadBalancer): loadBalancer is ILoadBalancerOption => Boolean(loadBalancer)),
  );
}

function getLoadBalancerIndex(command: IGceServerGroupCommand): Record<string, ILoadBalancerData> {
  return (command.backingData?.filtered?.loadBalancerIndex || {}) as Record<string, ILoadBalancerData>;
}

function getBackendServiceData(command: IGceServerGroupCommand, loadBalancerName: string): IBackendServiceData[] {
  const loadBalancer = getLoadBalancerIndex(command)[loadBalancerName];
  if (!Array.isArray(loadBalancer?.backendServices)) {
    return [];
  }
  const byName = new Map<string, IBackendServiceData>();
  loadBalancer.backendServices.forEach((backendService) => {
    const value =
      typeof backendService === 'string'
        ? { name: backendService }
        : { name: backendService.name || '', portName: backendService.portName };
    if (value.name && !byName.has(value.name)) {
      byName.set(value.name, value);
    }
  });
  return Array.from(byName.values());
}

function getNamedPortNames(command: IGceServerGroupCommand): string[] {
  return uniqueStrings(
    uniqueStrings(command.loadBalancers).flatMap((loadBalancerName) =>
      getBackendServiceData(command, loadBalancerName).map(({ portName }) => portName || ''),
    ),
  ).filter(Boolean);
}

function getBalancingModes(command: IGceServerGroupCommand): string[] {
  const loadBalancerIndex = getLoadBalancerIndex(command);
  const modeSets = uniqueStrings(command.loadBalancers)
    .map((loadBalancerName) => loadBalancerIndex[loadBalancerName]?.loadBalancerType)
    .filter((loadBalancerType): loadBalancerType is string => Boolean(loadBalancerType))
    .map((loadBalancerType) =>
      loadBalancerType === 'HTTP' || loadBalancerType === 'INTERNAL_MANAGED'
        ? HTTP_BALANCING_MODES
        : CONNECTION_BALANCING_MODES,
    );
  if (!modeSets.length) {
    const persistedMode = (command.loadBalancingPolicy as ILoadBalancingPolicy | undefined)?.balancingMode;
    return persistedMode ? [persistedMode] : [];
  }
  return modeSets.slice(1).reduce((modes, nextModes) => modes.filter((mode) => nextModes.includes(mode)), modeSets[0]);
}

function reconcileLoadBalancingPolicy(command: IGceServerGroupCommand): ILoadBalancingPolicy | undefined {
  const balancingModes = getBalancingModes(command);
  if (!balancingModes.length) {
    return undefined;
  }
  const existing = command.loadBalancingPolicy as ILoadBalancingPolicy | undefined;
  const balancingMode =
    existing?.balancingMode && balancingModes.includes(existing.balancingMode)
      ? existing.balancingMode
      : DEFAULT_LOAD_BALANCING_POLICY.balancingMode;
  const policy: ILoadBalancingPolicy = {
    ...existing,
    balancingMode,
    capacityScaler: existing?.capacityScaler ?? DEFAULT_LOAD_BALANCING_POLICY.capacityScaler,
    namedPorts: existing?.namedPorts ?? DEFAULT_LOAD_BALANCING_POLICY.namedPorts.map((namedPort) => ({ ...namedPort })),
  };
  if (existing?.balancingMode !== balancingMode) {
    MODE_LIMIT_FIELDS.forEach((field) => delete policy[field]);
  }
  if (balancingMode === 'UTILIZATION' && policy.maxUtilization === undefined) {
    policy.maxUtilization = DEFAULT_LOAD_BALANCING_POLICY.maxUtilization;
  }
  return policy;
}

function reconcileLoadBalancerMetadata(
  command: IGceServerGroupCommand,
  nextLoadBalancers: string[],
): Record<string, string[]> {
  const metadata = Object.entries(command.loadBalancerMetadata || {}).reduce<Record<string, string[]>>(
    (copy, [key, values]) => ({ ...copy, [key]: uniqueStrings(values) }),
    {},
  );
  uniqueStrings(command.loadBalancers).forEach((loadBalancerName) => {
    const reference = getLoadBalancerMetadataReference(command, loadBalancerName);
    if (reference) {
      metadata[reference.key] = (metadata[reference.key] || []).filter((name) => !reference.names.includes(name));
    }
  });
  nextLoadBalancers.forEach((loadBalancerName) => {
    const reference = getLoadBalancerMetadataReference(command, loadBalancerName);
    if (reference) {
      metadata[reference.key] = uniqueStrings([...(metadata[reference.key] || []), ...reference.names]);
    }
  });
  return metadata;
}

function getLoadBalancerMetadataReference(
  command: IGceServerGroupCommand,
  loadBalancerName: string,
): ILoadBalancerMetadataReference | undefined {
  const loadBalancer = getLoadBalancerIndex(command)[loadBalancerName];
  if (!loadBalancer) {
    return undefined;
  }
  if (loadBalancer.loadBalancerType === 'HTTP' || loadBalancer.loadBalancerType === 'INTERNAL_MANAGED') {
    const names = uniqueStrings((loadBalancer.listeners || []).map(({ name }) => name || ''));
    if (!names.length) {
      return undefined;
    }
    return {
      key: loadBalancer.loadBalancerType === 'HTTP' ? 'global-load-balancer-names' : 'load-balancer-names',
      names,
    };
  }
  return {
    key:
      loadBalancer.loadBalancerType === 'SSL' || loadBalancer.loadBalancerType === 'TCP'
        ? 'global-load-balancer-names'
        : 'load-balancer-names',
    names: [loadBalancerName],
  };
}

function preserveReferences(
  availableLoadBalancers: ILoadBalancerOption[],
  selectedLoadBalancers: string[],
): Array<ILoadBalancerOption & { unavailable: boolean }> {
  const availableNames = new Set(availableLoadBalancers.map(({ name }) => name));
  return [
    ...availableLoadBalancers
      .map((loadBalancer) => ({ ...loadBalancer, unavailable: false }))
      .sort((left, right) => left.name.localeCompare(right.name)),
    ...selectedLoadBalancers.filter((name) => !availableNames.has(name)).map((name) => ({ name, unavailable: true })),
  ];
}

function preserveStringReferences(availableValues: string[], persistedValues: string[]): IStringReference[] {
  const available = uniqueStrings(availableValues);
  return [
    ...available.map((value) => ({ unavailable: false, value })),
    ...uniqueStrings(persistedValues)
      .filter((value) => !available.includes(value))
      .map((value) => ({ unavailable: true, value })),
  ];
}

function isPipelineMode(command: IGceServerGroupCommand): boolean {
  return command.viewState.mode === 'createPipeline' || command.viewState.mode === 'editPipeline';
}

function isExpression(value: unknown): boolean {
  return typeof value === 'string' && /^\s*\$\{.+\}\s*$/.test(value);
}

function isValidBoundedValue(
  value: unknown,
  command: IGceServerGroupCommand,
  minimum: number,
  maximum: number,
): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  if (!hasNumericValue(value)) {
    return false;
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue >= minimum && numericValue <= maximum;
}

function isValidMinimum(value: unknown, command: IGceServerGroupCommand, minimum: number): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  if (!hasNumericValue(value)) {
    return false;
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue >= minimum;
}

function isValidInteger(value: unknown, command: IGceServerGroupCommand, minimum: number, maximum: number): boolean {
  if (isPipelineMode(command) && isExpression(value)) {
    return true;
  }
  if (!hasNumericValue(value)) {
    return false;
  }
  const numericValue = Number(value);
  return (
    Number.isFinite(numericValue) &&
    Number.isInteger(numericValue) &&
    numericValue >= minimum &&
    numericValue <= maximum
  );
}

function hasNumericValue(value: unknown): boolean {
  return value !== null && value !== undefined && (typeof value !== 'string' || Boolean(value.trim()));
}

function parsePercentageValue(value: string): number | string {
  if (isExpression(value)) {
    return value;
  }
  return value.trim() ? Number(value) / 100 : '';
}

function parseNumericValue(value: string): number | string {
  if (isExpression(value)) {
    return value;
  }
  return value.trim() ? Number(value) : '';
}

function toPercentageValue(value: number | string | undefined): number | string {
  if (typeof value === 'number') {
    return value * 100;
  }
  return value === undefined ? '' : value;
}

function isLoadBalancerInRegion(loadBalancer: any, command: IGceServerGroupCommand): boolean {
  return loadBalancer?.region === command.region || loadBalancer?.region === 'global';
}

function toLoadBalancerOption(loadBalancer: unknown): ILoadBalancerOption | null {
  if (typeof loadBalancer === 'string') {
    return { name: loadBalancer };
  }
  if (loadBalancer && typeof (loadBalancer as ILoadBalancerOption).name === 'string') {
    const { name, region } = loadBalancer as ILoadBalancerOption;
    return { name, region };
  }
  return null;
}

function uniqueLoadBalancers(loadBalancers: ILoadBalancerOption[]): ILoadBalancerOption[] {
  return Array.from(new Map(loadBalancers.map((loadBalancer) => [loadBalancer.name, loadBalancer])).values());
}

function uniqueStrings(values: unknown): string[] {
  if (!Array.isArray(values)) {
    return [];
  }
  return Array.from(new Set(values.filter((value): value is string => typeof value === 'string' && Boolean(value))));
}
