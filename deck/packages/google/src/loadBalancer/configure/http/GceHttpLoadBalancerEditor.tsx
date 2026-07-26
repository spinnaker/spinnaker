import React from 'react';

import { GceHttpLoadBalancerListenerEditor } from './GceHttpLoadBalancerListenerEditor';
import {
  GceHttpLoadBalancerBackendServiceEditor,
  GceHttpLoadBalancerHealthCheckEditor,
} from './GceHttpLoadBalancerResourceEditors';
import { GceHttpLoadBalancerHostRuleEditor } from './GceHttpLoadBalancerRoutingEditor';
import type {
  GceLoadBalancerType,
  IGceLoadBalancerCommand,
  IGceLoadBalancerData,
  IGceLoadBalancerDataItem,
  IGceLoadBalancerListener,
  IGceResourceReference,
} from '../common';
import { mergeGceResourceOptions } from '../common';

import './httpLoadBalancerWizard.component.less';

export interface IGceHttpLoadBalancerEditorProps {
  command: IGceLoadBalancerCommand;
  data: IGceLoadBalancerData;
  onChange: (command: IGceLoadBalancerCommand) => void;
}

interface IGceHttpLoadBalancerDataItem extends IGceLoadBalancerDataItem {
  account?: string;
  id?: string;
  network?: string;
  region?: string;
}

export function FormRow({ children, label }: { children: React.ReactNode; label: string }): JSX.Element {
  return (
    <div className="form-group">
      <label className="col-md-2 sm-label-right">{label}</label>
      <div className="col-md-8">{children}</div>
    </div>
  );
}

export interface IGceHttpLoadBalancerOptions extends IGceLoadBalancerData {
  accounts: IGceHttpLoadBalancerDataItem[];
  addresses: IGceHttpLoadBalancerDataItem[];
  backendServices: IGceHttpLoadBalancerDataItem[];
  certificates: IGceHttpLoadBalancerDataItem[];
  healthChecks: IGceHttpLoadBalancerDataItem[];
  networks: IGceHttpLoadBalancerDataItem[];
  regions: IGceHttpLoadBalancerDataItem[];
  subnets: IGceHttpLoadBalancerDataItem[];
}

function uniqueOptions<T extends { name?: string }>(options: T[]): Array<T & { name: string }> {
  return options.filter(
    (option, index): option is T & { name: string } =>
      Boolean(option.name) && options.findIndex(({ name }) => name === option.name) === index,
  );
}

export function buildGceHttpLoadBalancerOptions(
  command: IGceLoadBalancerCommand,
  data: IGceLoadBalancerData,
): IGceHttpLoadBalancerOptions {
  const internal = command.loadBalancerType === 'INTERNAL_MANAGED';
  const accountMatches = (item: IGceHttpLoadBalancerDataItem): boolean => item.account === command.credentials;
  const locationMatches = (item: IGceHttpLoadBalancerDataItem): boolean =>
    accountMatches(item) &&
    (internal ? item.region === command.region : item.region === undefined || item.region === 'global');
  const networks = (data.networks as IGceHttpLoadBalancerDataItem[]).filter(accountMatches);
  const selectedNetwork = networks.find(
    (network) => network.name === command.network?.name || network.id === command.network?.name,
  );
  const networkId = (command.network?.id as string | undefined) || selectedNetwork?.id || command.network?.name;
  const hostBackendServices = command.hostRules.flatMap((hostRule) => [
    ...(hostRule.pathMatcher.defaultService ? [hostRule.pathMatcher.defaultService] : []),
    ...hostRule.pathMatcher.pathRules.flatMap((pathRule) => (pathRule.backendService ? [pathRule.backendService] : [])),
  ]);
  const backendHealthChecks = command.backendServices.flatMap((service) =>
    service.healthCheck ? [service.healthCheck] : [],
  );

  return {
    accounts: mergeGceResourceOptions(data.accounts, command.credentials ? [{ name: command.credentials }] : []),
    addresses: mergeGceResourceOptions(
      (data.addresses as IGceHttpLoadBalancerDataItem[]).filter(locationMatches),
      uniqueOptions(command.listeners.flatMap((listener) => (listener.address ? [listener.address] : []))),
    ),
    backendServices: mergeGceResourceOptions(
      (data.backendServices as IGceHttpLoadBalancerDataItem[]).filter(locationMatches),
      uniqueOptions([
        ...command.backendServices,
        ...(command.defaultService ? [command.defaultService] : []),
        ...hostBackendServices,
      ]),
    ),
    certificates: mergeGceResourceOptions(
      (data.certificates as IGceHttpLoadBalancerDataItem[]).filter(locationMatches),
      uniqueOptions(command.listeners.flatMap((listener) => (listener.certificate ? [listener.certificate] : []))),
    ),
    healthChecks: mergeGceResourceOptions(
      (data.healthChecks as IGceHttpLoadBalancerDataItem[]).filter(locationMatches),
      uniqueOptions([...command.healthChecks, ...backendHealthChecks]),
    ),
    networks: mergeGceResourceOptions(networks, command.network ? [command.network] : []),
    regions: mergeGceResourceOptions(data.regions, command.region ? [{ name: command.region }] : []),
    subnets: mergeGceResourceOptions(
      internal
        ? (data.subnets as IGceHttpLoadBalancerDataItem[]).filter(
            (subnet) => accountMatches(subnet) && subnet.region === command.region && subnet.network === networkId,
          )
        : [],
      uniqueOptions([
        ...(command.subnet ? [command.subnet] : []),
        ...command.listeners.flatMap((listener) => (listener.subnet ? [listener.subnet] : [])),
      ]),
    ),
  };
}

export function constrainGceHttpLoadBalancerCommand(command: IGceLoadBalancerCommand): IGceLoadBalancerCommand {
  const constrainListener = ({ subnet, ...listener }: IGceLoadBalancerListener, keepSubnet: boolean) => {
    if (listener.protocol === 'HTTPS') {
      return { ...listener, portRange: '443', protocol: 'HTTPS' as const, ...(keepSubnet && subnet ? { subnet } : {}) };
    }
    const { certificate: _certificate, ...plaintextListener } = listener;
    return { ...plaintextListener, protocol: 'HTTP' as const, ...(keepSubnet && subnet ? { subnet } : {}) };
  };

  if (command.loadBalancerType === 'INTERNAL_MANAGED') {
    return {
      ...command,
      listeners: command.listeners.map((listener) => constrainListener(listener, true)),
      region: command.region === 'global' ? '' : command.region,
    };
  }

  return {
    ...command,
    listeners: command.listeners.map((listener) => constrainListener(listener, false)),
    loadBalancerType: 'HTTP',
    network: undefined,
    region: 'global',
    subnet: undefined,
  };
}

export function validateGceHttpLoadBalancerCommand(command: IGceLoadBalancerCommand): string[] {
  const errors = new Set<string>();
  const internal = command.loadBalancerType === 'INTERNAL_MANAGED';

  if (!command.name.trim()) errors.add('Name is required.');
  if (!command.credentials) errors.add('Account is required.');
  if (internal) {
    if (!command.region || command.region === 'global') {
      errors.add('Region is required for INTERNAL_MANAGED load balancers.');
    }
    if (!command.network?.name) errors.add('Network is required for INTERNAL_MANAGED load balancers.');
    if (!command.subnet?.name) errors.add('Subnet is required for INTERNAL_MANAGED load balancers.');
  } else if (command.region !== 'global') {
    errors.add('HTTP load balancers must use the global location.');
  }

  if (!command.listeners.length) errors.add('At least one listener is required.');
  command.listeners.forEach((listener) => {
    if (!listener.name.trim()) errors.add('Listener name is required.');
    if (listener.protocol !== 'HTTP' && listener.protocol !== 'HTTPS') {
      errors.add('Listener protocol must be HTTP or HTTPS.');
    }
    if (!validPort(listener.portRange)) {
      errors.add('Listener port must be a single port between 1 and 65535.');
    }
    if (listener.protocol === 'HTTPS' && !listener.certificate?.name) {
      errors.add('Certificate is required for HTTPS listeners.');
    }
    if (listener.protocol === 'HTTPS' && listener.portRange !== '443') {
      errors.add('HTTPS listeners must use port 443.');
    }
  });

  if (!command.backendServices.length) errors.add('At least one backend service is required.');
  command.backendServices.forEach((backendService) => {
    if (!backendService.name.trim()) errors.add('Backend service name is required.');
    if (!backendService.healthCheck?.name) errors.add('Each backend service requires a health check.');
    if (!backendService.portName) errors.add('Backend service port name is required.');
    if (negative(backendService.connectionDrainingTimeoutSec)) {
      errors.add('Connection draining timeout must be zero or greater.');
    }
    if (
      backendService.sessionAffinity === 'GENERATED_COOKIE' &&
      !numberInRange(backendService.affinityCookieTtlSec, 0, 86400)
    ) {
      errors.add('Generated cookie TTL must be between 0 and 86400 seconds.');
    }
  });

  command.healthChecks.forEach((healthCheck) => {
    if (!healthCheck.name?.trim()) errors.add('Health check name is required.');
    if (!['HTTP', 'HTTPS', 'TCP', 'SSL'].includes(String(healthCheck.healthCheckType))) {
      errors.add('Health check protocol must be HTTP, HTTPS, TCP, or SSL.');
    }
    if (!validPort(healthCheck.port)) errors.add('Health check port must be between 1 and 65535.');
    if (
      (healthCheck.healthCheckType === 'HTTP' || healthCheck.healthCheckType === 'HTTPS') &&
      !healthCheck.requestPath
    ) {
      errors.add('HTTP and HTTPS health checks require a request path.');
    }
    if (negative(healthCheck.timeoutSec)) errors.add('Health check timeout must be zero or greater.');
    if (healthCheck.checkIntervalSec !== undefined && Number(healthCheck.checkIntervalSec) <= 0) {
      errors.add('Health check interval must be greater than zero.');
    }
    if (healthCheck.healthyThreshold !== undefined && Number(healthCheck.healthyThreshold) <= 0) {
      errors.add('Healthy threshold must be greater than zero.');
    }
    if (healthCheck.unhealthyThreshold !== undefined && Number(healthCheck.unhealthyThreshold) <= 0) {
      errors.add('Unhealthy threshold must be greater than zero.');
    }
  });

  if (!command.defaultService?.name) errors.add('Default backend service is required.');
  command.hostRules.forEach((hostRule) => {
    if (!hostRule.hostPatterns.length) errors.add('Host rules require at least one host pattern.');
    if (!hostRule.pathMatcher.defaultService?.name) {
      errors.add('Path matcher default backend service is required.');
    }
    hostRule.pathMatcher.pathRules.forEach((pathRule) => {
      if (!pathRule.paths.length) errors.add('Path rules require at least one path.');
      if (!pathRule.backendService?.name) errors.add('Path rules require a backend service.');
    });
  });

  return Array.from(errors);
}

function validPort(value: unknown): boolean {
  const text = String(value ?? '').trim();
  if (!/^\d+$/.test(text)) return false;
  const port = Number(text);
  return Number.isInteger(port) && port >= 1 && port <= 65535;
}

function negative(value: unknown): boolean {
  return value !== undefined && Number(value) < 0;
}

function numberInRange(value: unknown, minimum: number, maximum: number): boolean {
  const number = Number(value);
  return value !== undefined && Number.isFinite(number) && number >= minimum && number <= maximum;
}

function selectedReference(name: string, options: IGceLoadBalancerDataItem[]): IGceResourceReference | undefined {
  if (!name) {
    return undefined;
  }
  return (options.find((option) => option.name === name) || { name }) as IGceResourceReference;
}

export function GceHttpLoadBalancerEditor({ command, data, onChange }: IGceHttpLoadBalancerEditorProps): JSX.Element {
  const options = buildGceHttpLoadBalancerOptions(command, data);
  const editing = command.mode === 'edit';

  const updateListener = (index: number, listener: IGceLoadBalancerListener): void => {
    const listeners = command.listeners.map((current, currentIndex) => (currentIndex === index ? listener : current));
    onChange(constrainGceHttpLoadBalancerCommand({ ...command, listeners }));
  };

  const update = (updates: Partial<IGceLoadBalancerCommand>): void => {
    onChange(constrainGceHttpLoadBalancerCommand({ ...command, ...updates } as IGceLoadBalancerCommand));
  };

  const updateBackendService = (
    index: number,
    backendService: IGceLoadBalancerCommand['backendServices'][number],
  ): void => {
    const backendServices = command.backendServices.map((current, currentIndex) =>
      currentIndex === index ? backendService : current,
    );
    const selectedHealthCheck = (backendService.healthCheck
      ? options.healthChecks.find(({ name }) => name === backendService.healthCheck?.name)
      : undefined) as IGceLoadBalancerCommand['healthChecks'][number] | undefined;
    const healthChecks =
      selectedHealthCheck && !command.healthChecks.some(({ name }) => name === selectedHealthCheck.name)
        ? [...command.healthChecks, selectedHealthCheck]
        : command.healthChecks;
    update({ backendServices, healthChecks });
  };

  const updateRouting = (
    hostRules: IGceLoadBalancerCommand['hostRules'],
    defaultService = command.defaultService,
  ): void => {
    const references = [
      ...(defaultService ? [defaultService] : []),
      ...hostRules.flatMap((hostRule) => [
        ...(hostRule.pathMatcher.defaultService ? [hostRule.pathMatcher.defaultService] : []),
        ...hostRule.pathMatcher.pathRules.flatMap((pathRule) =>
          pathRule.backendService ? [pathRule.backendService] : [],
        ),
      ]),
    ];
    const backendServices = [...command.backendServices];
    references.forEach((reference) => {
      if (!backendServices.some(({ name }) => name === reference.name)) {
        const selected = options.backendServices.find(({ name }) => name === reference.name) || reference;
        backendServices.push(selected as IGceLoadBalancerCommand['backendServices'][number]);
      }
    });
    const healthChecks = [...command.healthChecks];
    backendServices.forEach((service) => {
      if (service.healthCheck && !healthChecks.some(({ name }) => name === service.healthCheck?.name)) {
        const selected = options.healthChecks.find(({ name }) => name === service.healthCheck?.name);
        healthChecks.push((selected || service.healthCheck) as IGceLoadBalancerCommand['healthChecks'][number]);
      }
    });
    update({ backendServices, defaultService, healthChecks, hostRules });
  };

  return (
    <div className="gce-http-load-balancer-editor">
      <section>
        <h4>Location</h4>
        <FormRow label="Name">
          <input
            className="form-control input-sm"
            data-testid="load-balancer-name"
            maxLength={63}
            required
            type="text"
            disabled={editing}
            value={command.name}
            onChange={(event) => update({ name: event.target.value })}
          />
        </FormRow>
        <FormRow label="Type">
          <select
            className="form-control input-sm"
            data-testid="load-balancer-type"
            disabled={editing}
            value={command.loadBalancerType}
            onChange={(event) => update({ loadBalancerType: event.target.value as GceLoadBalancerType })}
          >
            <option value="HTTP">HTTP(S)</option>
            <option value="INTERNAL_MANAGED">Internal managed HTTP</option>
          </select>
        </FormRow>
        <FormRow label="Account">
          <select
            className="form-control input-sm"
            data-testid="credentials"
            disabled={editing}
            required
            value={command.credentials}
            onChange={(event) => update({ credentials: event.target.value })}
          >
            <option value="">Select...</option>
            {options.accounts.map((account) => (
              <option key={account.name} value={account.name}>
                {account.name}
              </option>
            ))}
          </select>
        </FormRow>
        {command.loadBalancerType === 'INTERNAL_MANAGED' && (
          <React.Fragment>
            <FormRow label="Region">
              <select
                className="form-control input-sm"
                data-testid="region"
                disabled={editing}
                required
                value={command.region}
                onChange={(event) => update({ region: event.target.value })}
              >
                <option value="">Select...</option>
                {options.regions.map((region) => (
                  <option key={region.name} value={region.name}>
                    {region.name}
                  </option>
                ))}
              </select>
            </FormRow>
            <FormRow label="Network">
              <select
                className="form-control input-sm"
                data-testid="network"
                required
                value={command.network?.name || ''}
                onChange={(event) => update({ network: selectedReference(event.target.value, options.networks) })}
              >
                <option value="">Select...</option>
                {options.networks.map((network) => (
                  <option key={network.name} value={network.name}>
                    {network.name}
                  </option>
                ))}
              </select>
            </FormRow>
            <FormRow label="Subnet">
              <select
                className="form-control input-sm"
                data-testid="subnet"
                required
                value={command.subnet?.name || ''}
                onChange={(event) => {
                  const subnet = selectedReference(event.target.value, options.subnets);
                  update({
                    subnet,
                    listeners: command.listeners.map((listener) => ({ ...listener, subnet })),
                  });
                }}
              >
                <option value="">Select...</option>
                {options.subnets.map((subnet) => (
                  <option key={subnet.name} value={subnet.name}>
                    {subnet.name}
                  </option>
                ))}
              </select>
            </FormRow>
          </React.Fragment>
        )}
      </section>
      <section>
        <h4>Listeners</h4>
        {command.listeners.map((listener, index) => (
          <GceHttpLoadBalancerListenerEditor
            addresses={options.addresses}
            certificates={options.certificates}
            key={index}
            listener={listener}
            loadBalancerType={command.loadBalancerType}
            onChange={(updated) => updateListener(index, updated)}
            onRemove={() =>
              update({ listeners: command.listeners.filter((_, currentIndex) => currentIndex !== index) })
            }
            subnets={options.subnets}
          />
        ))}
        <button
          type="button"
          className="add-new btn btn-block"
          onClick={() =>
            update({
              listeners: [
                ...command.listeners,
                {
                  name: command.name,
                  portRange: '80',
                  protocol: 'HTTP',
                  subnet: command.loadBalancerType === 'INTERNAL_MANAGED' ? command.subnet : undefined,
                },
              ],
            })
          }
        >
          Add listener
        </button>
      </section>
      <section>
        <h4>Backend services</h4>
        {command.backendServices.map((backendService, index) => (
          <GceHttpLoadBalancerBackendServiceEditor
            backendService={backendService}
            backendServices={options.backendServices}
            healthChecks={options.healthChecks}
            key={index}
            loadBalancerType={command.loadBalancerType}
            onChange={(updated) => updateBackendService(index, updated)}
            onRemove={() =>
              update({
                backendServices: command.backendServices.filter((_, currentIndex) => currentIndex !== index),
              })
            }
          />
        ))}
        <button
          type="button"
          className="add-new btn btn-block"
          onClick={() =>
            update({
              backendServices: [...command.backendServices, { name: '', portName: 'http', sessionAffinity: 'NONE' }],
            })
          }
        >
          Add backend service
        </button>
      </section>
      <section>
        <h4>Default service</h4>
        <FormRow label="Default backend service">
          <select
            className="form-control input-sm"
            data-testid="default-backend-service"
            required
            value={command.defaultService?.name || ''}
            onChange={(event) =>
              updateRouting(command.hostRules, selectedReference(event.target.value, options.backendServices))
            }
          >
            <option value="">Select...</option>
            {options.backendServices.map((service) => (
              <option key={service.name} value={service.name}>
                {service.name}
              </option>
            ))}
          </select>
        </FormRow>
      </section>
      <section>
        <h4>Host and path rules</h4>
        {command.hostRules.map((hostRule, index) => (
          <GceHttpLoadBalancerHostRuleEditor
            backendServices={options.backendServices}
            hostRule={hostRule}
            key={index}
            onChange={(updated) =>
              updateRouting(
                command.hostRules.map((current, currentIndex) => (currentIndex === index ? updated : current)),
              )
            }
            onRemove={() => updateRouting(command.hostRules.filter((_, currentIndex) => currentIndex !== index))}
          />
        ))}
        <button
          type="button"
          className="add-new btn btn-block"
          onClick={() =>
            updateRouting([
              ...command.hostRules,
              {
                hostPatterns: [],
                pathMatcher: { defaultService: command.defaultService, pathRules: [] },
              },
            ])
          }
        >
          Add host rule
        </button>
      </section>
      <section>
        <h4>Health checks</h4>
        {command.healthChecks.map((healthCheck, index) => (
          <GceHttpLoadBalancerHealthCheckEditor
            healthCheck={healthCheck}
            healthChecks={options.healthChecks}
            key={index}
            onChange={(updated) =>
              update({
                healthChecks: command.healthChecks.map((current, currentIndex) =>
                  currentIndex === index ? updated : current,
                ),
              })
            }
            onRemove={() =>
              update({ healthChecks: command.healthChecks.filter((_, currentIndex) => currentIndex !== index) })
            }
          />
        ))}
        <button
          type="button"
          className="add-new btn btn-block"
          onClick={() =>
            update({
              healthChecks: [
                ...command.healthChecks,
                {
                  checkIntervalSec: 10,
                  healthCheckType: 'HTTP',
                  name: '',
                  port: 80,
                  requestPath: '/',
                  timeoutSec: 5,
                  healthyThreshold: 10,
                  unhealthyThreshold: 2,
                },
              ],
            })
          }
        >
          Add health check
        </button>
      </section>
    </div>
  );
}
