export const GCE_LOAD_BALANCER_TYPES = ['NETWORK', 'INTERNAL', 'TCP', 'SSL', 'HTTP', 'INTERNAL_MANAGED'] as const;

export type GceLoadBalancerType = typeof GCE_LOAD_BALANCER_TYPES[number];
export type GceLoadBalancerEditorMode = 'create' | 'edit' | 'pipeline';
export type GceLoadBalancerProtocol = 'TCP' | 'UDP' | 'HTTP' | 'HTTPS' | 'HTTP2' | 'GRPC' | 'SSL';

export interface IGceLoadBalancerCapabilities {
  address: boolean;
  backendServices: boolean;
  certificates: boolean;
  healthChecks: boolean;
  hostRules: boolean;
  network: boolean;
  subnet: boolean;
}

export const GCE_LOAD_BALANCER_CAPABILITIES: Record<GceLoadBalancerType, IGceLoadBalancerCapabilities> = {
  NETWORK: capabilities({ address: true, healthChecks: true }),
  INTERNAL: capabilities({
    address: true,
    backendServices: true,
    healthChecks: true,
    network: true,
    subnet: true,
  }),
  TCP: capabilities({ address: true, backendServices: true, healthChecks: true }),
  SSL: capabilities({ address: true, backendServices: true, certificates: true, healthChecks: true }),
  HTTP: capabilities({
    address: true,
    backendServices: true,
    certificates: true,
    healthChecks: true,
    hostRules: true,
  }),
  INTERNAL_MANAGED: capabilities({
    address: true,
    backendServices: true,
    certificates: true,
    healthChecks: true,
    hostRules: true,
    network: true,
    subnet: true,
  }),
};

export interface IGceResourceReference {
  name: string;
  selfLink?: string;
  [key: string]: unknown;
}

export interface IGceLoadBalancerListener {
  name: string;
  protocol: GceLoadBalancerProtocol;
  portRange: string;
  address?: IGceResourceReference;
  certificate?: IGceResourceReference;
  certificateMap?: string;
  subnet?: IGceResourceReference;
}

export interface IGceLoadBalancerHealthCheck {
  checkIntervalSec?: number;
  grpcServiceName?: string;
  healthCheckType?: GceLoadBalancerProtocol;
  healthyThreshold?: number;
  host?: string;
  name?: string;
  port?: number;
  proxyHeader?: string;
  requestPath?: string;
  timeoutSec?: number;
  unhealthyThreshold?: number;
  useServingPort?: boolean;
  [key: string]: unknown;
}

export interface IGceLoadBalancerBackendService {
  name: string;
  healthCheck?: IGceResourceReference;
  [key: string]: unknown;
}

export interface IGceLoadBalancerPathRule {
  backendService?: IGceResourceReference;
  paths: string[];
}

export interface IGceLoadBalancerPathMatcher {
  defaultService?: IGceResourceReference;
  pathRules: IGceLoadBalancerPathRule[];
}

export interface IGceLoadBalancerHostRule {
  hostPatterns: string[];
  pathMatcher: IGceLoadBalancerPathMatcher;
}

export interface IGceLoadBalancerOriginalState {
  backendServices: IGceLoadBalancerBackendService[];
  healthChecks: IGceLoadBalancerHealthCheck[];
  listeners: IGceLoadBalancerListener[];
}

interface IGceLoadBalancerCommandBase {
  mode: GceLoadBalancerEditorMode;
  name: string;
  credentials: string;
  region: string;
  loadBalancerType: GceLoadBalancerType;
  listeners: IGceLoadBalancerListener[];
  healthChecks: IGceLoadBalancerHealthCheck[];
  backendServices: IGceLoadBalancerBackendService[];
  hostRules: IGceLoadBalancerHostRule[];
  defaultService?: IGceResourceReference;
  network?: IGceResourceReference;
  original?: IGceLoadBalancerOriginalState;
  subnet?: IGceResourceReference;
}

export interface IGceCreateLoadBalancerCommand extends IGceLoadBalancerCommandBase {
  mode: 'create';
}

export interface IGceEditLoadBalancerCommand extends IGceLoadBalancerCommandBase {
  mode: 'edit';
}

export interface IGcePipelineLoadBalancerCommand extends IGceLoadBalancerCommandBase {
  mode: 'pipeline';
}

export type IGceLoadBalancerCommand =
  | IGceCreateLoadBalancerCommand
  | IGceEditLoadBalancerCommand
  | IGcePipelineLoadBalancerCommand;

export interface IGceSerializedLoadBalancerCommand {
  [key: string]: unknown;
  cloudProvider: 'gce';
  credentials: string;
  loadBalancerName: string;
  loadBalancerType: GceLoadBalancerType;
  name: string;
  provider: 'gce';
  region: string;
  type: 'upsertLoadBalancer';
}

type UnknownRecord = Record<string, unknown>;

export function normalizeGceLoadBalancerCommand(
  persisted: UnknownRecord,
  mode: GceLoadBalancerEditorMode,
): IGceLoadBalancerCommand {
  const loadBalancerType = normalizeLoadBalancerType(persisted.loadBalancerType);
  const name = asString(
    isHttpType(loadBalancerType)
      ? persisted.urlMapName || persisted.name || persisted.loadBalancerName
      : persisted.name || persisted.loadBalancerName,
  );
  const topLevelListener = {
    address: persisted.ipAddress || persisted.address,
    certificate: persisted.certificate,
    name: isHttpType(loadBalancerType) ? persisted.loadBalancerName || persisted.name || name : name,
    portRange: persisted.portRange || persisted.ports,
    protocol:
      persisted.protocol ||
      (isHttpType(loadBalancerType) ? (persisted.certificate ? 'HTTPS' : 'HTTP') : persisted.ipProtocol),
    subnet: persisted.subnet,
  };
  const listeners = asArray(persisted.listeners).length ? asArray(persisted.listeners) : [topLevelListener];
  const backendServices = collectBackendServices(persisted);
  const healthChecks = collectHealthChecks(persisted, backendServices);

  const command = {
    mode,
    name,
    credentials: asString(persisted.credentials || persisted.account),
    region: asString(persisted.region) || defaultRegion(loadBalancerType),
    loadBalancerType,
    listeners: listeners.map((listener) => normalizeListener(asRecord(listener), loadBalancerType, name)),
    healthChecks,
    backendServices,
    hostRules: asArray(persisted.hostRules).map((hostRule) => normalizeHostRule(asRecord(hostRule))),
    defaultService: normalizeReference(persisted.defaultService),
    network: normalizeReference(persisted.network),
    subnet: normalizeReference(persisted.subnet),
  } as IGceLoadBalancerCommand;

  if (mode === 'edit') {
    command.original = cloneValue({ backendServices, healthChecks, listeners: command.listeners });
  }
  return command;
}

export function serializeGceLoadBalancerCommand(command: IGceLoadBalancerCommand): IGceSerializedLoadBalancerCommand {
  const capabilitiesForType = GCE_LOAD_BALANCER_CAPABILITIES[command.loadBalancerType];
  const listener = command.listeners[0];
  const serialized: IGceSerializedLoadBalancerCommand = {
    cloudProvider: 'gce',
    credentials: command.credentials,
    loadBalancerName: command.name,
    loadBalancerType: command.loadBalancerType,
    name: command.name,
    provider: 'gce',
    region: command.region,
    type: 'upsertLoadBalancer',
  };

  if (isHttpType(command.loadBalancerType)) {
    serialized.listeners = command.listeners.map((currentListener) =>
      serializeListener(currentListener, capabilitiesForType),
    );
  } else if (listener) {
    serialized.ipProtocol = listener.protocol;
    serialized.portRange = listener.portRange;
  }
  if (capabilitiesForType.address && listener?.address) {
    serialized.ipAddress = serializeReservedAddress(listener.address);
  }
  if (capabilitiesForType.certificates && listener?.certificate) {
    serialized.certificate = serializeCertificateName(listener.certificate);
  }
  if (capabilitiesForType.healthChecks && command.healthChecks.length) {
    serialized.healthChecks = command.healthChecks.map((healthCheck) => ({ ...healthCheck }));
  }
  if (capabilitiesForType.backendServices && command.backendServices.length) {
    serialized.backendServices = command.backendServices.map(serializeBackendService);
  }
  if (capabilitiesForType.hostRules && command.hostRules.length) {
    serialized.hostRules = command.hostRules.map(serializeHostRule);
  }
  if (capabilitiesForType.backendServices && command.defaultService) {
    serialized.defaultService = serializeBackendServiceName(command.defaultService);
  }
  if (capabilitiesForType.network && command.network) {
    serialized.network = serializeNetworkName(command.network);
  }
  if (capabilitiesForType.subnet && command.subnet) {
    serialized.subnet = serializeSubnetName(command.subnet);
  }

  return serialized;
}

function capabilities(overrides: Partial<IGceLoadBalancerCapabilities>): IGceLoadBalancerCapabilities {
  return {
    address: false,
    backendServices: false,
    certificates: false,
    healthChecks: false,
    hostRules: false,
    network: false,
    subnet: false,
    ...overrides,
  };
}

function normalizeLoadBalancerType(value: unknown): GceLoadBalancerType {
  const normalized = asString(value).toUpperCase() as GceLoadBalancerType;
  return GCE_LOAD_BALANCER_TYPES.includes(normalized) ? normalized : 'NETWORK';
}

function normalizeProtocol(value: unknown, type: GceLoadBalancerType): GceLoadBalancerProtocol {
  const normalized = asString(value).toUpperCase();
  if (['TCP', 'UDP', 'HTTP', 'HTTPS', 'HTTP2', 'GRPC', 'SSL'].includes(normalized)) {
    return normalized as GceLoadBalancerProtocol;
  }
  if (type === 'SSL') {
    return 'SSL';
  }
  return isHttpType(type) ? 'HTTP' : 'TCP';
}

function normalizeListener(
  listener: UnknownRecord,
  type: GceLoadBalancerType,
  fallbackName: string,
): IGceLoadBalancerListener {
  const normalized: IGceLoadBalancerListener = {
    name: asString(listener.name) || fallbackName,
    protocol: normalizeProtocol(
      listener.protocol ||
        (isHttpType(type)
          ? listener.certificate || listener.certificateMap || Number(listener.port || listener.portRange) === 443
            ? 'HTTPS'
            : 'HTTP'
          : listener.ipProtocol),
      type,
    ),
    portRange: normalizePortRange(listener.portRange || listener.port || listener.ports),
  };
  const address = normalizeReference(listener.address || listener.ipAddress);
  const certificate = normalizeReference(listener.certificate);
  const certificateMap = asString(listener.certificateMap);
  const subnet = normalizeReference(listener.subnet);
  if (address) {
    normalized.address = address;
  }
  if (certificate) {
    normalized.certificate = certificate;
  }
  if (certificateMap) {
    normalized.certificateMap = certificateMap;
  }
  if (subnet) {
    normalized.subnet = subnet;
  }
  return normalized;
}

export function normalizeGceHealthCheck(value: unknown): IGceLoadBalancerHealthCheck {
  const healthCheck = asRecord(value);
  const normalized: IGceLoadBalancerHealthCheck = { ...healthCheck };
  const healthCheckType = asString(healthCheck.healthCheckType || healthCheck.protocol);
  if (healthCheckType) {
    normalized.healthCheckType = normalizeProtocol(healthCheckType, 'NETWORK');
  }
  (['port', 'checkIntervalSec', 'timeoutSec', 'healthyThreshold', 'unhealthyThreshold'] as const).forEach((field) => {
    if (healthCheck[field] !== undefined && healthCheck[field] !== null && healthCheck[field] !== '') {
      normalized[field] = Number(healthCheck[field]);
    }
  });
  if (healthCheck.requestPath) {
    const requestPath = asString(healthCheck.requestPath);
    normalized.requestPath = requestPath.startsWith('/') ? requestPath : `/${requestPath}`;
  }
  return normalized;
}

function normalizeBackendService(service: UnknownRecord): IGceLoadBalancerBackendService {
  const normalized: IGceLoadBalancerBackendService = { ...service, name: asString(service.name) };
  const healthCheck = normalizeReference(service.healthCheck || service.healthCheckLink);
  if (healthCheck) {
    normalized.healthCheck = healthCheck;
  }
  delete normalized.healthCheckLink;
  return normalized;
}

function collectBackendServices(persisted: UnknownRecord): IGceLoadBalancerBackendService[] {
  const candidates = [...asArray(persisted.backendServices), ...asArray(persisted.backendService)];
  if (persisted.defaultService && typeof persisted.defaultService === 'object') {
    candidates.push(persisted.defaultService);
  }
  asArray(persisted.hostRules).forEach((hostRuleValue) => {
    const pathMatcher = asRecord(asRecord(hostRuleValue).pathMatcher);
    if (pathMatcher.defaultService && typeof pathMatcher.defaultService === 'object') {
      candidates.push(pathMatcher.defaultService);
    }
    asArray(pathMatcher.pathRules).forEach((pathRuleValue) => {
      const backendService = asRecord(pathRuleValue).backendService;
      if (backendService && typeof backendService === 'object') {
        candidates.push(backendService);
      }
    });
  });

  const services = new Map<string, IGceLoadBalancerBackendService>();
  candidates.forEach((candidate) => {
    const service = normalizeBackendService(
      typeof candidate === 'object' ? asRecord(candidate) : { name: asString(candidate) },
    );
    if (service.name && !services.has(service.name)) {
      services.set(service.name, service);
    }
  });
  return Array.from(services.values());
}

function collectHealthChecks(
  persisted: UnknownRecord,
  backendServices: IGceLoadBalancerBackendService[],
): IGceLoadBalancerHealthCheck[] {
  const candidates = [...asArray(persisted.healthChecks), ...asArray(persisted.healthCheck)];
  backendServices.forEach((service) => {
    if (service.healthCheck && Object.keys(service.healthCheck).some((key) => key !== 'name' && key !== 'selfLink')) {
      candidates.push(service.healthCheck);
    }
  });
  const healthChecks = new Map<string, IGceLoadBalancerHealthCheck>();
  candidates.forEach((candidate, index) => {
    const healthCheck = normalizeGceHealthCheck(asRecord(candidate));
    const key = healthCheck.name || (Object.keys(healthCheck).length ? `unnamed-${index}` : '');
    if (key && !healthChecks.has(key)) {
      healthChecks.set(key, healthCheck);
    }
  });
  return Array.from(healthChecks.values());
}

function normalizeHostRule(hostRule: UnknownRecord): IGceLoadBalancerHostRule {
  const pathMatcher = asRecord(hostRule.pathMatcher);
  return {
    hostPatterns: asStringArray(hostRule.hostPatterns),
    pathMatcher: {
      defaultService: normalizeReference(pathMatcher.defaultService),
      pathRules: asArray(pathMatcher.pathRules).map((pathRule) => {
        const rule = asRecord(pathRule);
        return {
          backendService: normalizeReference(rule.backendService),
          paths: asStringArray(rule.paths),
        };
      }),
    },
  };
}

function normalizeReference(value: unknown): IGceResourceReference | undefined {
  if (!value) {
    return undefined;
  }
  if (typeof value === 'object') {
    const reference = asRecord(value);
    const name = asString(reference.name || reference.id || reference.address || reference.selfLink);
    return name ? ({ ...reference, name } as IGceResourceReference) : undefined;
  }
  const reference = asString(value);
  const name = reference.split('/').filter(Boolean).pop() || reference;
  return reference.includes('/') ? { name, selfLink: reference } : { name };
}

function serializeReservedAddress(reference: IGceResourceReference): string {
  return asString(reference.address) || reference.name;
}

function serializeCertificateName(reference: IGceResourceReference): string {
  return reference.name;
}

function serializeNetworkName(reference: IGceResourceReference): string {
  return reference.name;
}

function serializeSubnetName(reference: IGceResourceReference): string {
  return reference.name;
}

function serializeBackendServiceName(reference: IGceResourceReference): string {
  return reference.name;
}

function serializeHealthCheckName(reference: IGceResourceReference): string {
  return reference.name;
}

function serializeListener(
  listener: IGceLoadBalancerListener,
  capabilitiesForType: IGceLoadBalancerCapabilities,
): UnknownRecord {
  const serialized: UnknownRecord = {
    name: listener.name,
    portRange: listener.protocol === 'HTTPS' ? '443' : listener.portRange,
    protocol: listener.protocol,
  };
  if (capabilitiesForType.address && listener.address) {
    serialized.ipAddress = serializeReservedAddress(listener.address);
  }
  if (capabilitiesForType.certificates && listener.certificate) {
    serialized.certificate = serializeCertificateName(listener.certificate);
  }
  if (capabilitiesForType.certificates && listener.certificateMap) {
    serialized.certificateMap = listener.certificateMap;
  }
  if (capabilitiesForType.subnet && listener.subnet) {
    serialized.subnet = serializeSubnetName(listener.subnet);
  }
  return serialized;
}

function serializeBackendService(service: IGceLoadBalancerBackendService): UnknownRecord {
  const serialized: UnknownRecord = { ...service };
  if (service.healthCheck) {
    serialized.healthCheck = serializeHealthCheckName(service.healthCheck);
  }
  return serialized;
}

function serializeHostRule(hostRule: IGceLoadBalancerHostRule): UnknownRecord {
  return {
    hostPatterns: [...hostRule.hostPatterns],
    pathMatcher: {
      defaultService: hostRule.pathMatcher.defaultService
        ? serializeBackendServiceName(hostRule.pathMatcher.defaultService)
        : undefined,
      pathRules: hostRule.pathMatcher.pathRules.map((pathRule) => ({
        backendService: pathRule.backendService ? serializeBackendServiceName(pathRule.backendService) : undefined,
        paths: [...pathRule.paths],
      })),
    },
  };
}

function normalizePortRange(value: unknown): string {
  return Array.isArray(value) ? value.map(String).join(',') : asString(value);
}

function defaultRegion(type: GceLoadBalancerType): string {
  return ['TCP', 'SSL', 'HTTP'].includes(type) ? 'global' : '';
}

function isHttpType(type: GceLoadBalancerType): boolean {
  return type === 'HTTP' || type === 'INTERNAL_MANAGED';
}

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === 'object' ? (value as UnknownRecord) : {};
}

function asArray(value: unknown): unknown[] {
  if (value === undefined || value === null) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}

function asString(value: unknown): string {
  return value === undefined || value === null ? '' : String(value);
}

function asStringArray(value: unknown): string[] {
  return asArray(value).map(asString).filter(Boolean);
}

function cloneValue<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map(cloneValue) as T;
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneValue(item)])) as T;
  }
  return value;
}
