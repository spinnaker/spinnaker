import React from 'react';

import { mergeGceResourceOptions } from './gceLoadBalancerData';
import type { IGceLoadBalancerData, IGceLoadBalancerDataItem } from './gceLoadBalancerData';
import type {
  GceLoadBalancerProtocol,
  IGceLoadBalancerBackendService,
  IGceLoadBalancerCommand,
  IGceLoadBalancerHealthCheck,
  IGceResourceReference,
} from './gceLoadBalancerModels';
import { normalizeGceHealthCheck } from './gceLoadBalancerModels';

export type GceProxyLoadBalancerType = 'INTERNAL' | 'TCP' | 'SSL';

export interface IGceProxyTypeConfig {
  backendProtocols: readonly GceLoadBalancerProtocol[];
  frontendProtocols: readonly GceLoadBalancerProtocol[];
}

export const GCE_PROXY_TYPE_CONFIG: Record<GceProxyLoadBalancerType, IGceProxyTypeConfig> = {
  INTERNAL: { frontendProtocols: ['TCP', 'UDP'], backendProtocols: ['TCP', 'UDP'] },
  TCP: { frontendProtocols: ['TCP'], backendProtocols: ['TCP'] },
  SSL: { frontendProtocols: ['SSL'], backendProtocols: ['TCP'] },
};

const PROXY_PORTS = ['25', '43', '110', '143', '195', '443', '465', '587', '700', '993', '995'];
const HEALTH_CHECK_PROTOCOLS: GceLoadBalancerProtocol[] = ['HTTP', 'HTTPS', 'TCP', 'SSL'];
const PROXY_SESSION_AFFINITIES = ['NONE', 'CLIENT_IP', 'GENERATED_COOKIE'];
const INTERNAL_SESSION_AFFINITIES = ['NONE', 'CLIENT_IP', 'CLIENT_IP_PROTO', 'CLIENT_IP_PORT_PROTO'];

interface IGceProxyDataItem extends IGceLoadBalancerDataItem {
  account?: string;
  credentials?: string;
  network?: string | IGceResourceReference;
  region?: string;
}

export interface IGceProxyResourceOptions extends IGceLoadBalancerData {
  accounts: IGceProxyDataItem[];
  addresses: IGceProxyDataItem[];
  backendServices: IGceProxyDataItem[];
  certificates: IGceProxyDataItem[];
  healthChecks: IGceProxyDataItem[];
  networks: IGceProxyDataItem[];
  regions: IGceProxyDataItem[];
  subnets: IGceProxyDataItem[];
}

export interface IGceProxyLoadBalancerEditorProps {
  command: IGceLoadBalancerCommand;
  data: IGceLoadBalancerData;
  disabled?: boolean;
  onChange: (command: IGceLoadBalancerCommand) => void;
}

export function applyGceProxyTypeConstraints(command: IGceLoadBalancerCommand): IGceLoadBalancerCommand {
  const type = command.loadBalancerType as GceProxyLoadBalancerType;
  const config = GCE_PROXY_TYPE_CONFIG[type];
  if (!config) {
    return command;
  }

  const originalListener = command.listeners[0] || {
    name: command.name,
    portRange: '',
    protocol: config.frontendProtocols[0],
  };
  const frontendProtocol = config.frontendProtocols.includes(originalListener.protocol)
    ? originalListener.protocol
    : config.frontendProtocols[0];
  const originalBackend = command.backendServices[0];
  const backendProtocol =
    type === 'INTERNAL'
      ? config.backendProtocols.includes(frontendProtocol)
        ? frontendProtocol
        : config.backendProtocols[0]
      : config.backendProtocols[0];
  const backendService: IGceLoadBalancerBackendService = {
    ...(originalBackend || {
      name: command.name,
      portName: type === 'INTERNAL' ? undefined : 'http',
      sessionAffinity: 'NONE',
    }),
    protocol: backendProtocol,
  };
  if (frontendProtocol === 'UDP') {
    backendService.sessionAffinity = 'NONE';
    delete backendService.affinityCookieTtlSec;
  }

  return {
    ...command,
    region: type === 'INTERNAL' ? command.region : 'global',
    listeners: [
      {
        ...originalListener,
        name: originalListener.name || command.name,
        portRange: originalListener.portRange || (type === 'INTERNAL' ? '' : '443'),
        protocol: frontendProtocol,
      },
    ],
    backendServices: [backendService],
  } as IGceLoadBalancerCommand;
}

export function getGceProxyResourceOptions(
  command: IGceLoadBalancerCommand,
  data: IGceLoadBalancerData,
): IGceProxyResourceOptions {
  const listener = command.listeners[0];
  const backend = command.backendServices[0];
  const accountScoped = (items: IGceProxyDataItem[]) =>
    items.filter((item) =>
      !item.account && !item.credentials ? true : (item.account || item.credentials) === command.credentials,
    );
  const locationScoped = (items: IGceProxyDataItem[]) =>
    accountScoped(items).filter((item) => !item.region || item.region === command.region);
  const networkName = command.network?.name;
  const subnetScoped = accountScoped(data.subnets as IGceProxyDataItem[]).filter((item) => {
    const itemNetwork = typeof item.network === 'string' ? item.network : item.network?.name;
    return (!item.region || item.region === command.region) && (!itemNetwork || itemNetwork === networkName);
  });

  return {
    accounts: data.accounts,
    addresses: mergeGceResourceOptions(
      locationScoped(data.addresses as IGceProxyDataItem[]),
      references(listener?.address),
    ),
    backendServices: mergeGceResourceOptions(
      locationScoped(data.backendServices as IGceProxyDataItem[]),
      references(backend),
    ),
    certificates: mergeGceResourceOptions(
      accountScoped(data.certificates as IGceProxyDataItem[]),
      references(listener?.certificate),
    ),
    healthChecks: mergeGceResourceOptions(
      accountScoped(data.healthChecks as IGceProxyDataItem[]),
      references(backend?.healthCheck),
    ),
    networks: mergeGceResourceOptions(accountScoped(data.networks as IGceProxyDataItem[]), references(command.network)),
    regions: data.regions,
    subnets: mergeGceResourceOptions(subnetScoped, references(command.subnet)),
  };
}

export function validateGceProxyLoadBalancerCommand(command: IGceLoadBalancerCommand): string[] {
  const errors: string[] = [];
  const type = command.loadBalancerType as GceProxyLoadBalancerType;
  const listener = command.listeners[0];
  const backend = command.backendServices[0];
  const healthCheck = command.healthChecks[0];
  const ports = splitPorts(listener?.portRange);

  if (!command.name.trim()) errors.push('Name is required.');
  if (!command.credentials) errors.push('Account is required.');
  if (!command.region) errors.push('Region is required.');

  if (type === 'INTERNAL') {
    if (ports.length < 1 || ports.length > 5 || ports.some((port) => !validPort(port))) {
      errors.push('INTERNAL load balancers accept between one and five listener ports.');
    }
    if (!command.network) errors.push('Network is required for INTERNAL load balancers.');
    if (!command.subnet) errors.push('Subnet is required for INTERNAL load balancers.');
  } else if (ports.length !== 1 || !PROXY_PORTS.includes(ports[0])) {
    errors.push(`${type} proxy port must be one of 25, 43, 110, 143, 195, 443, 465, 587, 700, 993, or 995.`);
  }

  if (type === 'SSL' && !listener?.certificate) errors.push('Certificate is required for SSL proxy load balancers.');
  if (!backend?.name) errors.push('Backend service is required.');
  if (type !== 'INTERNAL' && !backend?.portName) errors.push('Port name is required.');
  if (type !== 'INTERNAL' && negative(backend?.connectionDrainingTimeoutSec)) {
    errors.push('Connection draining timeout must be zero or greater.');
  }
  if (
    backend?.sessionAffinity === 'GENERATED_COOKIE' &&
    (!numberInRange(backend.affinityCookieTtlSec, 0, 86400) || backend.affinityCookieTtlSec === undefined)
  ) {
    errors.push('Generated cookie TTL must be between 0 and 86400 seconds.');
  }

  if (!healthCheck?.name && !backend?.healthCheck?.name) errors.push('Health check name is required.');
  if (healthCheck) {
    if (!validPort(healthCheck.port)) errors.push('Health check port must be between 1 and 65535.');
    if (
      (healthCheck.healthCheckType === 'HTTP' || healthCheck.healthCheckType === 'HTTPS') &&
      !healthCheck.requestPath
    ) {
      errors.push('HTTP and HTTPS health checks require a request path.');
    }
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

export function GceProxyLoadBalancerEditor({
  command,
  data,
  disabled = false,
  onChange,
}: IGceProxyLoadBalancerEditorProps): JSX.Element {
  const constrained = applyGceProxyTypeConstraints(command);
  const type = constrained.loadBalancerType as GceProxyLoadBalancerType;
  const listener = constrained.listeners[0];
  const backend = constrained.backendServices[0];
  const healthCheck = constrained.healthChecks[0] || ({ healthCheckType: 'TCP' } as IGceLoadBalancerHealthCheck);
  const options = getGceProxyResourceOptions(constrained, data);
  const sessionAffinities =
    type === 'INTERNAL'
      ? listener.protocol === 'UDP'
        ? ['NONE']
        : INTERNAL_SESSION_AFFINITIES
      : PROXY_SESSION_AFFINITIES;

  const update = (updates: Partial<IGceLoadBalancerCommand>) =>
    onChange(applyGceProxyTypeConstraints({ ...constrained, ...updates } as IGceLoadBalancerCommand));
  const updateListener = (updates: Record<string, unknown>) =>
    update({ listeners: [{ ...listener, ...updates }] } as Partial<IGceLoadBalancerCommand>);
  const updateBackend = (updates: Record<string, unknown>) =>
    update({ backendServices: [{ ...backend, ...updates }] } as Partial<IGceLoadBalancerCommand>);
  const updateHealthCheck = (updates: Partial<IGceLoadBalancerHealthCheck>) =>
    update({ healthChecks: [{ ...healthCheck, ...updates }] } as Partial<IGceLoadBalancerCommand>);
  const updateName = (name: string) =>
    update({
      name,
      listeners: [{ ...listener, name: listener.name === constrained.name ? name : listener.name }],
      backendServices: [{ ...backend, name: backend.name === constrained.name ? name : backend.name }],
    });
  const selectHealthCheck = (name: string) => {
    if (!name) return updateBackend({ healthCheck: undefined });
    const selected = options.healthChecks.find((healthCheckOption) => healthCheckOption.name === name);
    const healthCheck = normalizeGceHealthCheck(selected || { name });
    const selfLink = typeof healthCheck.selfLink === 'string' ? healthCheck.selfLink : undefined;
    const healthCheckReference = {
      name,
      ...(selfLink ? { selfLink } : {}),
    };
    update({ backendServices: [{ ...backend, healthCheck: healthCheckReference }], healthChecks: [healthCheck] });
  };

  return (
    <div className="form-horizontal gce-proxy-load-balancer-editor">
      {textField('Name', 'name', constrained.name, updateName, disabled)}
      {selectField(
        'Account',
        'credentials',
        constrained.credentials,
        options.accounts,
        (credentials) => update({ credentials }),
        disabled,
      )}
      {type === 'INTERNAL'
        ? selectField('Region', 'region', constrained.region, options.regions, (region) => update({ region }), disabled)
        : textField('Region', 'region', constrained.region, () => undefined, true)}
      {type === 'INTERNAL' &&
        selectField(
          'Network',
          'network',
          constrained.network?.name,
          options.networks,
          (name) => update({ network: reference(name), subnet: undefined }),
          disabled,
        )}
      {type === 'INTERNAL' &&
        selectField(
          'Subnet',
          'subnet',
          constrained.subnet?.name,
          options.subnets,
          (name) => update({ subnet: reference(name) }),
          disabled,
        )}
      {selectField(
        'Address',
        'address',
        listener.address?.name,
        options.addresses,
        (name) => updateListener({ address: selectedResource(name, options.addresses) }),
        disabled,
      )}
      {selectField(
        'Protocol',
        'protocol',
        listener.protocol,
        GCE_PROXY_TYPE_CONFIG[type].frontendProtocols.map((name) => ({ name })),
        (protocol) => updateListener({ protocol }),
        disabled || GCE_PROXY_TYPE_CONFIG[type].frontendProtocols.length === 1,
      )}
      {type === 'INTERNAL'
        ? textField(
            'Listener ports',
            'portRange',
            listener.portRange,
            (portRange) => updateListener({ portRange }),
            disabled,
          )
        : selectField(
            'Listener port',
            'portRange',
            listener.portRange,
            PROXY_PORTS.map((name) => ({ name })),
            (portRange) => updateListener({ portRange }),
            disabled,
          )}
      {type === 'SSL' &&
        selectField('Certificate', 'certificate', listener.certificate?.name, options.certificates, (name) =>
          updateListener({ certificate: reference(name) }),
        )}

      <h4>Backend Service</h4>
      {selectField(
        'Backend service',
        'backendService',
        backend.name,
        options.backendServices,
        (name) => updateBackend({ name }),
        disabled,
        true,
      )}
      {type !== 'INTERNAL' &&
        textField('Port name', 'portName', String(backend.portName || ''), (portName) => updateBackend({ portName }))}
      {type !== 'INTERNAL' &&
        numberField(
          'Connection draining timeout',
          'connectionDrainingTimeoutSec',
          backend.connectionDrainingTimeoutSec,
          (connectionDrainingTimeoutSec) => updateBackend({ connectionDrainingTimeoutSec }),
        )}
      {selectField(
        'Session affinity',
        'sessionAffinity',
        String(backend.sessionAffinity || 'NONE'),
        sessionAffinities.map((name) => ({ name })),
        (sessionAffinity) => updateBackend({ sessionAffinity }),
      )}
      {backend.sessionAffinity === 'GENERATED_COOKIE' &&
        numberField(
          'Affinity cookie TTL',
          'affinityCookieTtlSec',
          backend.affinityCookieTtlSec,
          (affinityCookieTtlSec) => updateBackend({ affinityCookieTtlSec }),
        )}

      <h4>Health Check</h4>
      {selectField(
        'Existing health check',
        'healthCheck',
        backend.healthCheck?.name,
        options.healthChecks,
        selectHealthCheck,
      )}
      {selectField(
        'Health check protocol',
        'healthCheckType',
        healthCheck.healthCheckType,
        HEALTH_CHECK_PROTOCOLS.map((name) => ({ name })),
        (healthCheckType) =>
          updateHealthCheck({
            healthCheckType: healthCheckType as GceLoadBalancerProtocol,
          }),
      )}
      {textField('Health check name', 'healthCheckName', healthCheck.name || '', (name) => updateHealthCheck({ name }))}
      {(healthCheck.healthCheckType === 'HTTP' || healthCheck.healthCheckType === 'HTTPS') &&
        textField('Request path', 'requestPath', healthCheck.requestPath || '', (requestPath) =>
          updateHealthCheck({ requestPath }),
        )}
      {numberField('Health check port', 'healthCheckPort', healthCheck.port, (port) => updateHealthCheck({ port }))}
      {numberField('Timeout', 'timeoutSec', healthCheck.timeoutSec, (timeoutSec) => updateHealthCheck({ timeoutSec }))}
      {numberField('Interval', 'checkIntervalSec', healthCheck.checkIntervalSec, (checkIntervalSec) =>
        updateHealthCheck({ checkIntervalSec }),
      )}
      {numberField('Healthy threshold', 'healthyThreshold', healthCheck.healthyThreshold, (healthyThreshold) =>
        updateHealthCheck({ healthyThreshold }),
      )}
      {numberField('Unhealthy threshold', 'unhealthyThreshold', healthCheck.unhealthyThreshold, (unhealthyThreshold) =>
        updateHealthCheck({ unhealthyThreshold }),
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
          type="number"
          value={value === undefined || value === null ? '' : String(value)}
          onChange={(event) => onChange(event.target.value === '' ? undefined : Number(event.target.value))}
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
  allowCustomValue = false,
): JSX.Element {
  const names = options.map(({ name }) => name);
  if (allowCustomValue && value && !names.includes(value)) names.push(value);
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
          {names.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

function references<T extends IGceLoadBalancerDataItem>(referenceValue?: T): T[] {
  return referenceValue?.name ? [referenceValue] : [];
}

function reference(name: string): IGceResourceReference | undefined {
  return name ? { name } : undefined;
}

function selectedResource(
  name: string,
  options: readonly IGceLoadBalancerDataItem[],
): IGceResourceReference | undefined {
  return name ? ((options.find((option) => option.name === name) || { name }) as IGceResourceReference) : undefined;
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

function negative(value: unknown): boolean {
  return value !== undefined && value !== null && Number(value) < 0;
}

function numberInRange(value: unknown, min: number, max: number): boolean {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max;
}
