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

export type GceNetworkSessionAffinity = 'NONE' | 'CLIENT_IP' | 'CLIENT_IP_PROTO';

export type IGceNetworkLoadBalancerCommand = IGceLoadBalancerCommand & {
  loadBalancerType: 'NETWORK';
  sessionAffinity: GceNetworkSessionAffinity;
  targetPool?: IGceResourceReference;
};

interface IGceNetworkDataItem extends IGceLoadBalancerDataItem {
  account?: string;
  credentials?: string;
  network?: string | IGceResourceReference;
  region?: string;
}

export interface IGceNetworkLoadBalancerOptions {
  accounts: IGceNetworkDataItem[];
  addresses: IGceNetworkDataItem[];
  networks: IGceNetworkDataItem[];
  regions: IGceNetworkDataItem[];
}

export interface IGceNetworkLoadBalancerEditorProps {
  command: IGceNetworkLoadBalancerCommand;
  data: IGceLoadBalancerData;
  onChange: (command: IGceNetworkLoadBalancerCommand) => void;
}

const SESSION_AFFINITIES: GceNetworkSessionAffinity[] = ['NONE', 'CLIENT_IP', 'CLIENT_IP_PROTO'];
const PROTOCOLS: GceLoadBalancerProtocol[] = ['TCP', 'UDP'];

export function createGceNetworkHealthCheck(): IGceLoadBalancerHealthCheck {
  return {
    checkIntervalSec: 10,
    healthCheckType: 'HTTP',
    healthyThreshold: 10,
    port: 80,
    requestPath: '/',
    timeoutSec: 5,
    unhealthyThreshold: 2,
  };
}

export function buildGceNetworkLoadBalancerOptions(
  command: IGceNetworkLoadBalancerCommand,
  data: IGceLoadBalancerData,
): IGceNetworkLoadBalancerOptions {
  const accountMatches = (item: IGceNetworkDataItem): boolean =>
    !item.account && !item.credentials ? true : (item.account || item.credentials) === command.credentials;
  const locationMatches = (item: IGceNetworkDataItem): boolean =>
    accountMatches(item) && (!item.region || item.region === command.region);
  const listener = command.listeners[0];

  return {
    accounts: mergeGceResourceOptions(data.accounts, command.credentials ? [{ name: command.credentials }] : []),
    addresses: mergeGceResourceOptions(
      (data.addresses as IGceNetworkDataItem[]).filter(locationMatches),
      references(listener?.address),
    ),
    networks: mergeGceResourceOptions(
      (data.networks as IGceNetworkDataItem[]).filter(accountMatches),
      references(command.network),
    ),
    regions: mergeGceResourceOptions(data.regions, command.region ? [{ name: command.region }] : []),
  };
}

export function validateGceNetworkLoadBalancerCommand(command: IGceNetworkLoadBalancerCommand): string[] {
  const errors: string[] = [];
  const listener = command.listeners[0];
  const healthCheck = command.healthChecks[0];

  if (!command.name.trim()) errors.push('Name is required.');
  if (!command.credentials) errors.push('Account is required.');
  if (!command.region) errors.push('Region is required.');
  if (!validPortRange(listener?.portRange)) errors.push('Port range must contain ports between 1 and 65535.');

  if (healthCheck) {
    if (!validPort(healthCheck.port)) errors.push('Health check port must be between 1 and 65535.');
    if (!healthCheck.requestPath) errors.push('Health check path is required.');
    if (negative(healthCheck.timeoutSec)) errors.push('Health check timeout must be zero or greater.');
    if (healthCheck.checkIntervalSec !== undefined && Number(healthCheck.checkIntervalSec) <= 0) {
      errors.push('Health check interval must be greater than zero.');
    }
    if (healthCheck.healthyThreshold !== undefined && Number(healthCheck.healthyThreshold) <= 0) {
      errors.push('Healthy threshold must be greater than zero.');
    }
    if (healthCheck.unhealthyThreshold !== undefined && Number(healthCheck.unhealthyThreshold) <= 0) {
      errors.push('Unhealthy threshold must be greater than zero.');
    }
  }

  return errors;
}

export function GceNetworkLoadBalancerEditor({
  command,
  data,
  onChange,
}: IGceNetworkLoadBalancerEditorProps): JSX.Element {
  const options = buildGceNetworkLoadBalancerOptions(command, data);
  const listener = command.listeners[0] || { name: command.name, portRange: '', protocol: 'TCP' };
  const healthCheck = command.healthChecks[0];
  const editing = command.mode === 'edit';
  const update = (updates: Partial<IGceNetworkLoadBalancerCommand>): void =>
    onChange({ ...command, ...updates } as IGceNetworkLoadBalancerCommand);
  const updateListener = (updates: Partial<typeof listener>): void =>
    update({ listeners: [{ ...listener, ...updates }] });
  const updateHealthCheck = (updates: Partial<IGceLoadBalancerHealthCheck>): void =>
    update({ healthChecks: [{ ...healthCheck, ...updates }] });
  const updateName = (name: string): void =>
    update({
      name,
      listeners: [{ ...listener, name: listener.name === command.name ? name : listener.name }],
    });

  return (
    <div className="form-horizontal gce-network-load-balancer-editor">
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
        'Network',
        'network',
        command.network?.name,
        options.networks,
        (name) => update({ network: selectedReference(name, options.networks) }),
        editing,
      )}

      <h4>Forwarding Rule</h4>
      {selectField('IP address', 'address', listener.address?.name, options.addresses, (name) =>
        updateListener({ address: selectedReference(name, options.addresses) }),
      )}
      {selectField(
        'Protocol',
        'protocol',
        listener.protocol,
        PROTOCOLS.map((name) => ({ name })),
        (protocol) => updateListener({ protocol: protocol as GceLoadBalancerProtocol }),
      )}
      {textField('Port range', 'portRange', listener.portRange, (portRange) => updateListener({ portRange }))}
      {textField('Target pool', 'targetPool', command.targetPool?.name || '', () => undefined, true)}
      {selectField(
        'Session affinity',
        'sessionAffinity',
        command.sessionAffinity,
        SESSION_AFFINITIES.map((name) => ({ name })),
        (sessionAffinity) => update({ sessionAffinity: sessionAffinity as GceNetworkSessionAffinity }),
        editing,
      )}

      <h4>Health Check</h4>
      <div className="form-group" data-field="healthCheckEnabled">
        <div className="col-md-7 col-md-offset-3">
          <label>
            <input
              checked={Boolean(healthCheck)}
              onChange={(event) =>
                update({ healthChecks: event.target.checked ? [createGceNetworkHealthCheck()] : [] })
              }
              type="checkbox"
            />{' '}
            Enable health check
          </label>
        </div>
      </div>
      {healthCheck && (
        <React.Fragment>
          {numberField('Port', 'healthCheckPort', healthCheck.port, (port) => updateHealthCheck({ port }))}
          {textField('Request path', 'requestPath', healthCheck.requestPath || '', (requestPath) =>
            updateHealthCheck({
              requestPath: requestPath && !requestPath.startsWith('/') ? `/${requestPath}` : requestPath,
            }),
          )}
          {numberField('Timeout', 'timeoutSec', healthCheck.timeoutSec, (timeoutSec) =>
            updateHealthCheck({ timeoutSec }),
          )}
          {numberField('Interval', 'checkIntervalSec', healthCheck.checkIntervalSec, (checkIntervalSec) =>
            updateHealthCheck({ checkIntervalSec }),
          )}
          {numberField('Healthy threshold', 'healthyThreshold', healthCheck.healthyThreshold, (healthyThreshold) =>
            updateHealthCheck({ healthyThreshold }),
          )}
          {numberField(
            'Unhealthy threshold',
            'unhealthyThreshold',
            healthCheck.unhealthyThreshold,
            (unhealthyThreshold) => updateHealthCheck({ unhealthyThreshold }),
          )}
        </React.Fragment>
      )}
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
  options: readonly IGceLoadBalancerDataItem[],
  onChange: (value: string) => void,
  disabled = false,
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
          <option value="">Select...</option>
          {options.map(({ name }) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

function selectedReference(
  name: string,
  options: readonly IGceLoadBalancerDataItem[],
): IGceResourceReference | undefined {
  return name ? ((options.find((option) => option.name === name) || { name }) as IGceResourceReference) : undefined;
}

function references<T extends IGceLoadBalancerDataItem>(referenceValue?: T): T[] {
  return referenceValue?.name ? [referenceValue] : [];
}

function validPort(value: unknown): boolean {
  const port = Number(value);
  return Number.isInteger(port) && port >= 1 && port <= 65535;
}

function validPortRange(value: string | undefined): boolean {
  const match = String(value || '').match(/^(\d+)(?:-(\d+))?$/);
  if (!match) return false;
  const start = Number(match[1]);
  const end = Number(match[2] || match[1]);
  return validPort(start) && validPort(end) && start <= end;
}

function negative(value: unknown): boolean {
  return value !== undefined && value !== null && Number(value) < 0;
}
