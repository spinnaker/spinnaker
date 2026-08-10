import React from 'react';

import type {
  GceLoadBalancerProtocol,
  IGceLoadBalancerCommand,
  IGceLoadBalancerData,
  IGceLoadBalancerDataItem,
  IGceLoadBalancerHealthCheck,
  IGceResourceReference,
} from '../common';
import { mergeGceResourceOptions } from '../common';

export type GceRegionalExternalNetworkSessionAffinity = 'NONE' | 'CLIENT_IP' | 'CLIENT_IP_PROTO';

export type IGceRegionalExternalNetworkLoadBalancerCommand = IGceLoadBalancerCommand & {
  loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK';
  ports: string[];
};

interface IGceRegionalExternalNetworkDataItem extends IGceLoadBalancerDataItem {
  account?: string;
  address?: string;
  addressType?: string;
  credentials?: string;
  networkTier?: string;
  region?: string;
}

export interface IGceRegionalExternalNetworkLoadBalancerOptions {
  accounts: IGceRegionalExternalNetworkDataItem[];
  addresses: IGceRegionalExternalNetworkDataItem[];
  regions: IGceRegionalExternalNetworkDataItem[];
}

export interface IGceRegionalExternalNetworkLoadBalancerEditorProps {
  command: IGceRegionalExternalNetworkLoadBalancerCommand;
  data: IGceLoadBalancerData;
  onChange: (command: IGceRegionalExternalNetworkLoadBalancerCommand) => void;
}

const SESSION_AFFINITIES: GceRegionalExternalNetworkSessionAffinity[] = ['NONE', 'CLIENT_IP', 'CLIENT_IP_PROTO'];
const PROTOCOLS: GceLoadBalancerProtocol[] = ['TCP', 'UDP'];
const MAX_PORTS = 5;

export function createGceRegionalExternalNetworkHealthCheck(): IGceLoadBalancerHealthCheck {
  return {
    healthCheckType: 'TCP',
    port: 80,
  };
}

export function buildGceRegionalExternalNetworkLoadBalancerOptions(
  command: IGceRegionalExternalNetworkLoadBalancerCommand,
  data: IGceLoadBalancerData,
): IGceRegionalExternalNetworkLoadBalancerOptions {
  const accountMatches = (item: IGceRegionalExternalNetworkDataItem): boolean =>
    !item.account && !item.credentials ? true : (item.account || item.credentials) === command.credentials;
  const locationMatches = (item: IGceRegionalExternalNetworkDataItem): boolean =>
    accountMatches(item) && (!item.region || item.region === command.region);
  const listener = command.listeners[0];

  return {
    accounts: mergeGceResourceOptions(data.accounts, command.credentials ? [{ name: command.credentials }] : []),
    addresses: mergeGceResourceOptions(
      (data.addresses as IGceRegionalExternalNetworkDataItem[])
        .filter(locationMatches)
        .filter((item) => item.addressType === 'EXTERNAL'),
      references(listener?.address),
    ),
    regions: mergeGceResourceOptions(data.regions, command.region ? [{ name: command.region }] : []),
  };
}

export function validateGceRegionalExternalNetworkLoadBalancerCommand(
  command: IGceRegionalExternalNetworkLoadBalancerCommand,
): string[] {
  const errors: string[] = [];
  const backend = command.backendServices[0];
  const ports = command.ports?.length ? command.ports : splitPorts(command.listeners[0]?.portRange);

  if (!command.name.trim()) errors.push('Name is required.');
  if (!command.credentials) errors.push('Account is required.');
  if (!command.region) errors.push('Region is required.');

  if (!ports.length) {
    errors.push('Ports must be between 1 and 65535.');
  } else {
    if (ports.length > MAX_PORTS) {
      errors.push('REGIONAL_EXTERNAL_NETWORK load balancers accept between one and five ports.');
    }
    if (ports.some((port) => !validPort(port))) {
      errors.push('Ports must be between 1 and 65535.');
    }
  }

  if (!backend?.name?.trim()) errors.push('Backend service name is required.');
  if (!hasHealthCheck(backend)) errors.push('Each backend service requires a health check.');

  const sessionAffinity = String(backend?.sessionAffinity || 'NONE').toUpperCase();
  if (!SESSION_AFFINITIES.includes(sessionAffinity as GceRegionalExternalNetworkSessionAffinity)) {
    errors.push('Session affinity must be NONE, CLIENT_IP, or CLIENT_IP_PROTO.');
  }

  return errors;
}

export function GceRegionalExternalNetworkLoadBalancerEditor({
  command,
  data,
  onChange,
}: IGceRegionalExternalNetworkLoadBalancerEditorProps): JSX.Element {
  const options = buildGceRegionalExternalNetworkLoadBalancerOptions(command, data);
  const listener = command.listeners[0] || { name: command.name, portRange: '', protocol: 'TCP' };
  const backend = command.backendServices[0] || { name: command.name, sessionAffinity: 'NONE' };
  const healthCheck =
    (typeof backend.healthCheck === 'object' ? backend.healthCheck : undefined) ||
    command.healthChecks[0] ||
    createGceRegionalExternalNetworkHealthCheck();
  const editing = command.mode === 'edit';
  const portsValue = (command.ports?.length ? command.ports : splitPorts(listener.portRange)).join(', ');

  const update = (updates: Partial<IGceRegionalExternalNetworkLoadBalancerCommand>): void =>
    onChange({ ...command, ...updates } as IGceRegionalExternalNetworkLoadBalancerCommand);
  const updateListener = (updates: Partial<typeof listener>): void =>
    update({ listeners: [{ ...listener, ...updates }] });
  const updateBackend = (updates: Record<string, unknown>): void =>
    update({ backendServices: [{ ...backend, ...updates }] });
  const updateHealthCheck = (updates: Partial<IGceLoadBalancerHealthCheck>): void => {
    const nextHealthCheck = { ...healthCheck, ...updates };
    update({
      backendServices: [{ ...backend, healthCheck: (nextHealthCheck as unknown) as IGceResourceReference }],
      healthChecks: [nextHealthCheck],
    });
  };
  const updateName = (name: string): void =>
    update({
      name,
      listeners: [{ ...listener, name: listener.name === command.name ? name : listener.name }],
      backendServices: [{ ...backend, name: backend.name === command.name ? name : backend.name }],
    });
  const updatePorts = (value: string): void => {
    const ports = value
      .split(',')
      .map((port) => port.trim())
      .filter(Boolean);
    update({
      ports,
      listeners: [{ ...listener, portRange: ports.join(',') }],
    });
  };
  const updateAddress = (addressValue: string): void => {
    const selected = (data.addresses as IGceRegionalExternalNetworkDataItem[]).find(
      (item) => item.address === addressValue || item.name === addressValue,
    );
    const address: IGceResourceReference | undefined = addressValue
      ? {
          ...(selected || {}),
          address: selected?.address || addressValue,
          name: selected?.address || selected?.name || addressValue,
        }
      : undefined;
    update({
      listeners: [{ ...listener, address }],
      networkTier: selected?.networkTier || (addressValue ? 'PREMIUM' : command.networkTier),
    });
  };

  return (
    <div className="form-horizontal gce-regional-external-network-load-balancer-editor">
      {textField('Name', 'name', command.name, updateName, editing)}
      {selectField(
        'Account',
        'credentials',
        command.credentials,
        options.accounts,
        (credentials) => update({ credentials }),
        editing,
      )}
      {selectField('Region', 'region', command.region, options.regions, (region) => update({ region }), editing)}
      {selectField(
        'IP address',
        'address',
        String(listener.address?.address || listener.address?.name || ''),
        options.addresses,
        updateAddress,
        editing,
        (item) => item.address || item.name,
      )}
      {textField('Network tier', 'networkTier', command.networkTier || '', () => undefined, true)}
      {selectField(
        'Protocol',
        'protocol',
        listener.protocol,
        PROTOCOLS.map((name) => ({ name })),
        (protocol) => updateListener({ protocol: protocol as GceLoadBalancerProtocol }),
        false,
        ({ name }) => name,
        false,
      )}
      {textField('Ports', 'ports', portsValue, updatePorts)}
      {selectField(
        'Session affinity',
        'sessionAffinity',
        String(backend.sessionAffinity || 'NONE'),
        SESSION_AFFINITIES.map((name) => ({ name })),
        (sessionAffinity) => updateBackend({ sessionAffinity }),
        false,
        ({ name }) => name,
        false,
      )}

      <h4>Health Check</h4>
      <div className="form-group" data-field="healthCheck">
        {numberField('Port', 'healthCheckPort', healthCheck.port, (port) => updateHealthCheck({ port }))}
      </div>
    </div>
  );
}

function textField(
  label: string,
  field: string,
  value: string,
  onChange: (value: string) => void,
  disabled = false,
): JSX.Element {
  return (
    <div className="form-group" data-field={field}>
      <label className="col-md-3 sm-label-right">{label}</label>
      <div className="col-md-7">
        <input
          className="form-control input-sm"
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          value={value || ''}
        />
      </div>
    </div>
  );
}

function numberField(
  label: string,
  field: string,
  value: unknown,
  onChange: (value: number | undefined) => void,
): JSX.Element {
  return (
    <div className="form-group" data-field={field}>
      <label className="col-md-3 sm-label-right">{label}</label>
      <div className="col-md-7">
        <input
          className="form-control input-sm"
          onChange={(event) => onChange(event.target.value === '' ? undefined : Number(event.target.value))}
          type="number"
          value={value === undefined || value === null ? '' : String(value)}
        />
      </div>
    </div>
  );
}

function selectField(
  label: string,
  field: string,
  value: string | undefined,
  options: readonly IGceRegionalExternalNetworkDataItem[],
  onChange: (value: string) => void,
  disabled = false,
  getValue: (item: IGceRegionalExternalNetworkDataItem) => string = ({ name }) => name,
  includeEmptyOption = true,
): JSX.Element {
  return (
    <div className="form-group" data-field={field}>
      <label className="col-md-3 sm-label-right">{label}</label>
      <div className="col-md-7">
        <select
          className="form-control input-sm"
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          value={value || ''}
        >
          {includeEmptyOption && <option value="">Select...</option>}
          {options.map((option) => {
            const optionValue = getValue(option);
            return (
              <option key={optionValue} value={optionValue}>
                {optionValue}
              </option>
            );
          })}
        </select>
      </div>
    </div>
  );
}

function references<T extends IGceLoadBalancerDataItem>(referenceValue?: T): T[] {
  return referenceValue?.name || (referenceValue as IGceRegionalExternalNetworkDataItem | undefined)?.address
    ? [referenceValue]
    : [];
}

function splitPorts(value: string | undefined): string[] {
  return String(value || '')
    .split(',')
    .map((port) => port.trim())
    .filter(Boolean);
}

function validPort(value: unknown): boolean {
  const port = Number(value);
  return Number.isInteger(port) && port >= 1 && port <= 65535;
}

function hasHealthCheck(backend?: { healthCheck?: unknown }): boolean {
  const healthCheck = backend?.healthCheck;
  if (!healthCheck) return false;
  if (typeof healthCheck === 'string') return Boolean(healthCheck);
  if (typeof healthCheck === 'object') {
    const record = healthCheck as Record<string, unknown>;
    return Boolean(record.healthCheckType || record.port || record.name);
  }
  return false;
}
