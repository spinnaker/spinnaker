import React from 'react';

import type {
  GceLoadBalancerType,
  IGceLoadBalancerBackendService,
  IGceLoadBalancerDataItem,
  IGceLoadBalancerHealthCheck,
  IGceResourceReference,
} from '../common';

function numberValue(value: string): number | undefined {
  return value === '' ? undefined : Number(value);
}

function selectedReference(name: string, options: IGceLoadBalancerDataItem[]): IGceResourceReference | undefined {
  if (!name) {
    return undefined;
  }
  return (options.find((option) => option.name === name) || { name }) as IGceResourceReference;
}

export interface IGceHttpLoadBalancerHealthCheckEditorProps {
  healthCheck: IGceLoadBalancerHealthCheck;
  healthChecks: IGceLoadBalancerDataItem[];
  onChange: (healthCheck: IGceLoadBalancerHealthCheck) => void;
  onRemove: () => void;
}

export function GceHttpLoadBalancerHealthCheckEditor({
  healthCheck,
  healthChecks,
  onChange,
  onRemove,
}: IGceHttpLoadBalancerHealthCheckEditorProps): JSX.Element {
  const updateNumber = (field: string, value: string): void =>
    onChange({ ...healthCheck, [field]: numberValue(value) });

  return (
    <fieldset className="gce-http-health-check">
      <legend>Health check</legend>
      <label>
        Existing health check
        <select
          data-testid="health-check-reference"
          value={healthCheck.name || ''}
          onChange={(event) => {
            const selected = healthChecks.find(({ name }) => name === event.target.value);
            onChange(selected ? ({ ...selected } as IGceLoadBalancerHealthCheck) : { ...healthCheck, name: '' });
          }}
        >
          <option value="">Create new...</option>
          {healthChecks.map((option) => (
            <option key={option.name} value={option.name}>
              {option.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Protocol
        <select
          data-testid="health-check-protocol"
          value={healthCheck.healthCheckType || 'HTTP'}
          onChange={(event) =>
            onChange({
              ...healthCheck,
              healthCheckType: event.target.value as IGceLoadBalancerHealthCheck['healthCheckType'],
            })
          }
        >
          {['HTTP', 'HTTPS', 'HTTP2', 'GRPC', 'TCP', 'SSL'].map((protocol) => (
            <option key={protocol} value={protocol}>
              {protocol}
            </option>
          ))}
        </select>
      </label>
      <label>
        Name
        <input
          data-testid="health-check-name"
          required
          type="text"
          value={healthCheck.name || ''}
          onChange={(event) => onChange({ ...healthCheck, name: event.target.value })}
        />
      </label>
      {(healthCheck.healthCheckType === 'HTTP' ||
        healthCheck.healthCheckType === 'HTTPS' ||
        healthCheck.healthCheckType === 'HTTP2') && (
        <label>
          Request path
          <input
            data-testid="health-check-path"
            required
            type="text"
            value={healthCheck.requestPath || ''}
            onChange={(event) => onChange({ ...healthCheck, requestPath: event.target.value })}
          />
        </label>
      )}
      {healthCheck.healthCheckType === 'GRPC' && (
        <label>
          Service name
          <input
            data-testid="health-check-grpc-service-name"
            type="text"
            value={healthCheck.grpcServiceName || ''}
            onChange={(event) => onChange({ ...healthCheck, grpcServiceName: event.target.value })}
          />
        </label>
      )}
      <label>
        Port
        <input
          data-testid="health-check-port"
          max={65535}
          min={1}
          required
          type="number"
          value={healthCheck.port ?? ''}
          onChange={(event) => updateNumber('port', event.target.value)}
        />
      </label>
      {[
        ['timeoutSec', 'Timeout'],
        ['checkIntervalSec', 'Interval'],
        ['healthyThreshold', 'Healthy threshold'],
        ['unhealthyThreshold', 'Unhealthy threshold'],
      ].map(([field, label]) => (
        <label key={field}>
          {label}
          <input
            data-testid={`health-check-${field}`}
            min={0}
            type="number"
            value={(healthCheck[field] as number | undefined) ?? ''}
            onChange={(event) => updateNumber(field, event.target.value)}
          />
        </label>
      ))}
      <button type="button" className="btn btn-sm btn-default" onClick={onRemove}>
        Remove health check
      </button>
    </fieldset>
  );
}

export interface IGceHttpLoadBalancerBackendServiceEditorProps {
  backendService: IGceLoadBalancerBackendService;
  backendServices: IGceLoadBalancerDataItem[];
  healthChecks: IGceLoadBalancerDataItem[];
  loadBalancerType: GceLoadBalancerType;
  onChange: (backendService: IGceLoadBalancerBackendService) => void;
  onRemove: () => void;
}

export function GceHttpLoadBalancerBackendServiceEditor({
  backendService,
  backendServices,
  healthChecks,
  loadBalancerType,
  onChange,
  onRemove,
}: IGceHttpLoadBalancerBackendServiceEditorProps): JSX.Element {
  return (
    <fieldset className="gce-http-backend-service">
      <legend>Backend service</legend>
      <label>
        Existing backend service
        <select
          data-testid="backend-service-reference"
          value={backendService.name}
          onChange={(event) => {
            const selected = backendServices.find(({ name }) => name === event.target.value);
            onChange(selected ? ({ ...selected } as IGceLoadBalancerBackendService) : { ...backendService, name: '' });
          }}
        >
          <option value="">Create new...</option>
          {backendServices.map((option) => (
            <option key={option.name} value={option.name}>
              {option.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Name
        <input
          data-testid="backend-service-name"
          required
          type="text"
          value={backendService.name}
          onChange={(event) => onChange({ ...backendService, name: event.target.value })}
        />
      </label>
      <label>
        Health check
        <select
          data-testid="backend-service-health-check"
          required
          value={backendService.healthCheck?.name || ''}
          onChange={(event) =>
            onChange({
              ...backendService,
              healthCheck: selectedReference(event.target.value, healthChecks),
            })
          }
        >
          <option value="">Select...</option>
          {healthChecks.map((option) => (
            <option key={option.name} value={option.name}>
              {option.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Session affinity
        <select
          data-testid="backend-service-session-affinity"
          value={String(backendService.sessionAffinity || 'NONE')}
          onChange={(event) => onChange({ ...backendService, sessionAffinity: event.target.value })}
        >
          {['NONE', 'CLIENT_IP', 'GENERATED_COOKIE', 'HEADER_FIELD', 'HTTP_COOKIE'].map((affinity) => (
            <option key={affinity} value={affinity}>
              {affinity}
            </option>
          ))}
        </select>
      </label>
      <label>
        Port name
        <input
          data-testid="backend-service-port-name"
          required
          type="text"
          value={String(backendService.portName || '')}
          onChange={(event) => onChange({ ...backendService, portName: event.target.value })}
        />
      </label>
      <label>
        Connection draining timeout
        <input
          data-testid="backend-service-draining"
          min={0}
          type="number"
          value={(backendService.connectionDrainingTimeoutSec as number | undefined) ?? ''}
          onChange={(event) =>
            onChange({ ...backendService, connectionDrainingTimeoutSec: numberValue(event.target.value) })
          }
        />
      </label>
      {loadBalancerType === 'HTTP' && (
        <label>
          <input
            data-testid="backend-service-cdn"
            checked={Boolean(backendService.enableCDN)}
            type="checkbox"
            onChange={(event) => onChange({ ...backendService, enableCDN: event.target.checked })}
          />
          Enable CDN
        </label>
      )}
      {backendService.sessionAffinity === 'GENERATED_COOKIE' && (
        <label>
          Affinity cookie TTL seconds
          <input
            data-testid="backend-service-cookie-ttl"
            max={86400}
            min={0}
            required
            type="number"
            value={(backendService.affinityCookieTtlSec as number | undefined) ?? ''}
            onChange={(event) => onChange({ ...backendService, affinityCookieTtlSec: numberValue(event.target.value) })}
          />
        </label>
      )}
      <button type="button" className="btn btn-sm btn-default" onClick={onRemove}>
        Remove backend service
      </button>
    </fieldset>
  );
}
