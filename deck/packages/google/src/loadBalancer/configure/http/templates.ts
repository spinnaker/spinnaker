export class HttpLoadBalancerTemplate {
  public provider = 'gce';
  public stack = '';
  public detail = '';
  public region = 'global';
  public loadBalancerType = 'HTTP';
  public certificate = '';
  public defaultService: BackendServiceTemplate;
  public hostRules: HostRuleTemplate[] = [];
  public listeners: ListenerTemplate[] = [];

  constructor(public credentials: string | null) {}
}

export class BackendServiceTemplate {
  public backends: any[] = [];
  public healthCheck: HealthCheckTemplate;
  public sessionAffinity = 'NONE';
  public affinityCookieTtlSec: number | null = null;
}

export class HealthCheckTemplate {
  public requestPath = '/';
  public port = 80;
  public checkIntervalSec = 10;
  public timeoutSec = 5;
  public healthyThreshold = 10;
  public unhealthyThreshold = 2;
}

export class PathMatcherTemplate {
  public pathRules: PathRuleTemplate[] = [];
}

export class HostRuleTemplate {
  public hostPatterns: string[];
  public pathMatcher: PathMatcherTemplate = new PathMatcherTemplate();
}

export class PathRuleTemplate {
  public paths: string[];
}

export class ListenerTemplate {
  public name: string;
  public port: number;
  public certificate: string | null = null;
}
