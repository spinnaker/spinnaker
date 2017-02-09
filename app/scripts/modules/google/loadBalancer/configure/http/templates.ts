export class HttpLoadBalancerTemplate {
  provider: string = 'gce';
  stack: string = '';
  detail: string = '';
  region: string = 'global';
  loadBalancerType: string = 'HTTP';
  certificate: string = '';
  defaultService: BackendServiceTemplate;
  hostRules: HostRuleTemplate[] = [];
  listeners: ListenerTemplate[] = [];

  constructor (public credentials: string | null) {}
}

export class BackendServiceTemplate {
  backends: any[] = [];
  healthCheck: HealthCheckTemplate;
  sessionAffinity: string = 'NONE';
  affinityCookieTtlSec: number | null = null;
}

export class HealthCheckTemplate {
  requestPath: string = '/';
  port = 80;
  checkIntervalSec = 10;
  timeoutSec = 5;
  healthyThreshold = 10;
  unhealthyThreshold = 2;
}

export class HostRuleTemplate {
  hostPatterns: string[];
  pathMatcher: PathMatcherTemplate = new PathMatcherTemplate();
}

export class PathMatcherTemplate {
  pathRules: PathRuleTemplate[] = [];
}

export class PathRuleTemplate {
  paths: string[];
}

export class ListenerTemplate {
  name: string;
  port: number;
  certificate: string | null = null;
}
