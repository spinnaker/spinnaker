import { isEqual } from 'lodash';

import type { Application, ITask, ITaskCommand } from '@spinnaker/core';
import { TaskExecutor } from '@spinnaker/core';

import type {
  IGceLoadBalancerBackendService,
  IGceLoadBalancerCommand,
  IGceLoadBalancerHealthCheck,
  IGceResourceReference,
  IGceSerializedLoadBalancerCommand,
} from './gceLoadBalancerModels';
import { serializeGceLoadBalancerCommand } from './gceLoadBalancerModels';

export interface IGceLoadBalancerSubmissionDependencies {
  application: Application;
  executeTask?: (taskCommand: ITaskCommand) => PromiseLike<ITask>;
}

export type GceLoadBalancerOperationList = IGceSerializedLoadBalancerCommand[];
export type GceLoadBalancerSubmissionResult =
  | IGceSerializedLoadBalancerCommand
  | GceLoadBalancerOperationList
  | PromiseLike<ITask>;

export function buildGceLoadBalancerJobs(command: IGceLoadBalancerCommand): GceLoadBalancerOperationList {
  const serialized = serializeGceLoadBalancerCommand(command);
  if (command.loadBalancerType !== 'HTTP' && command.loadBalancerType !== 'INTERNAL_MANAGED') {
    return [serialized];
  }

  const {
    backendServices: _backendServices,
    healthChecks: _healthChecks,
    hostRules: _hostRules,
    listeners,
    ...shared
  } = serialized;
  const backendServices = new Map(
    command.backendServices.map((service) => [service.name, serializeBackendService(service, command.healthChecks)]),
  );
  const defaultService = resolveBackendService(command.defaultService, backendServices);
  const hostRules = command.hostRules.flatMap((hostRule) =>
    hostRule.hostPatterns.map((hostPattern) => ({
      hostPatterns: [hostPattern],
      pathMatcher: {
        ...(hostRule.pathMatcher.defaultService
          ? { defaultService: resolveBackendService(hostRule.pathMatcher.defaultService, backendServices) }
          : {}),
        pathRules: hostRule.pathMatcher.pathRules.map((pathRule) => ({
          ...(pathRule.backendService
            ? { backendService: resolveBackendService(pathRule.backendService, backendServices) }
            : {}),
          paths: [...pathRule.paths],
        })),
      },
    })),
  );

  const jobs = ((listeners || []) as Array<Record<string, unknown>>).map((listener) => {
    const name = String(listener.name);
    return {
      ...shared,
      certificate: listener.certificate || null,
      ...(defaultService ? { defaultService } : {}),
      hostRules,
      ipAddress: listener.ipAddress,
      ipProtocol: 'TCP',
      loadBalancerName: name,
      name,
      portRange: listener.portRange,
      ...(listener.subnet ? { subnet: listener.subnet } : {}),
      urlMapName: command.name,
    } as IGceSerializedLoadBalancerCommand;
  });

  if (command.mode === 'edit' && command.original && jobs.length) {
    const listenerNames = new Set(command.listeners.map(({ name }) => name));
    const currentBackendServices = new Map(command.backendServices.map((service) => [service.name, service]));
    jobs[0].listenersToDelete = command.original.listeners
      .map(({ name }) => name)
      .filter((name) => !listenerNames.has(name));
    jobs[0].backendServiceDiff = command.original.backendServices
      .filter((service) => !isEqual(service, currentBackendServices.get(service.name)))
      .map((service) => serializeBackendService(service, command.original!.healthChecks));
  }

  return jobs;
}

function serializeBackendService(
  service: IGceLoadBalancerBackendService,
  healthChecks: IGceLoadBalancerHealthCheck[],
): Record<string, unknown> {
  const healthCheck = service.healthCheck
    ? healthChecks.find(({ name }) => name === service.healthCheck?.name) || service.healthCheck
    : undefined;
  return {
    ...service,
    ...(healthCheck ? { healthCheck: { ...healthCheck } } : {}),
  };
}

function resolveBackendService(
  reference: IGceResourceReference | undefined,
  services: Map<string, Record<string, unknown>>,
): Record<string, unknown> | undefined {
  return reference ? services.get(reference.name) || { ...reference } : undefined;
}

export function submitGceLoadBalancerCommand(
  command: IGceLoadBalancerCommand,
  { application, executeTask = TaskExecutor.executeTask }: IGceLoadBalancerSubmissionDependencies,
): GceLoadBalancerSubmissionResult {
  const jobs = buildGceLoadBalancerJobs(command);
  if (command.mode === 'pipeline') {
    return command.loadBalancerType === 'HTTP' || command.loadBalancerType === 'INTERNAL_MANAGED' ? jobs : jobs[0];
  }

  return executeTask({
    application,
    description: `${command.mode === 'edit' ? 'Update' : 'Create'} Load Balancer: ${command.name}`,
    job: jobs,
  });
}
