import { StateService } from '@uirouter/core';
import { isDate, isObject, isUndefined } from 'lodash';

import { ITask } from '../domain';
import { NameUtils } from '../naming';
import { ReactInjector } from '../reactShims';
import { Registry } from '../registry';

// TODO: refactor to marker interface and have input types declare expected fields
export interface IUrlBuilderInput {
  account?: string;
  application?: string;
  cluster?: string;
  detail?: string;
  instanceId?: string;
  loadBalancer?: string;
  name?: string;
  namespace?: string;
  project?: string;
  provider?: string;
  region?: string;
  serverGroup?: string;
  stack?: string;
  taskId?: string;
  type: string;
  vpcId?: string;
}

interface IParams {
  [key: string]: any;
}

export interface IUrlBuilder {
  build: (input: IUrlBuilderInput, $state: StateService) => string;
}

interface IClusterFilter {
  acct: string;
  q?: string;
  reg?: string;
  stack?: string;
}

class UrlBuilderUtils {
  private static forEachSorted(obj: any, iterator: Function, context?: any): void {
    const keys: string[] = Object.keys(obj).sort();
    keys.forEach((key: string) => {
      iterator.call(context, obj[key], key);
    });
  }

  private static encodeUriQuery(value: string): string {
    return encodeURIComponent(value)
      .replace(/%40/gi, '@')
      .replace(/%3A/gi, ':')
      .replace(/%24/g, '$')
      .replace(/%2C/gi, ',')
      .replace(/%20/g, '+');
  }

  public static buildUrl(url: string, params: IParams = {}): string {
    if (!params) {
      return url;
    }

    const parts: string[] = [];
    UrlBuilderUtils.forEachSorted(params, (value: any, key: string) => {
      if (value === null || isUndefined(value)) {
        return;
      }

      let val: any[];
      if (Array.isArray(value)) {
        val = value;
      } else {
        val = [value];
      }

      val.forEach((v: any) => {
        if (isObject(v)) {
          if (isDate(v)) {
            v = (v as Date).toISOString();
          } else {
            v = JSON.stringify(v);
          }
        }

        parts.push(`${UrlBuilderUtils.encodeUriQuery(key)}=${UrlBuilderUtils.encodeUriQuery(v)}`);
      });
    });

    if (parts.length > 0) {
      url += (url.includes('?') ? '&' : '?') + parts.join('&');
    }

    return url;
  }
}

class ApplicationsUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    let result: string;
    if (input.project) {
      result = $state.href(
        'home.project.application',
        { application: input.application, project: input.project },
        { inherit: false },
      );
    } else {
      result = $state.href(
        'home.applications.application',
        {
          application: input.application,
        },
        { inherit: false },
      );
    }

    return result;
  }
}

class ClustersUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    const filters: IClusterFilter = {
      acct: input.account,
    };
    if (input.cluster) {
      filters.q = `cluster:${input.cluster}`;
    }
    if (input.stack) {
      filters.stack = input.stack;
    }
    if (input.detail) {
      filters.q = `detail:${input.detail}`;
    }
    if (input.region) {
      filters.reg = input.region;
    }

    let href: string;
    if (input.project) {
      href = $state.href(
        'home.project.application.insight.clusters',
        {
          application: input.application,
          project: input.project,
        },
        { inherit: false },
      );
    } else {
      href = $state.href(
        'home.applications.application.insight.clusters',
        {
          application: input.application,
        },
        { inherit: false },
      );
    }

    return UrlBuilderUtils.buildUrl(href, filters);
  }
}

class InstancesUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    let result: string;
    if (!input.application) {
      result = $state.href('home.instanceDetails', {
        account: input.account,
        region: input.region,
        instanceId: input.instanceId,
        provider: input.provider,
      });
    } else {
      const href: string = $state.href(
        'home.applications.application.insight.clusters.instanceDetails',
        {
          application: input.application,
          instanceId: input.instanceId,
          provider: input.provider,
        },
        { inherit: false },
      );
      result = UrlBuilderUtils.buildUrl(href, { q: input.serverGroup, acct: input.account, reg: input.region });
    }

    return result;
  }
}

class LoadBalancersUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    const href: string = $state.href(
      'home.applications.application.insight.loadBalancers.loadBalancerDetails',
      {
        application: input.application,
        name: input.loadBalancer,
        region: input.region || input.namespace,
        accountId: input.account,
        vpcId: input.vpcId,
        provider: input.provider,
      },
      { inherit: false },
    );

    return UrlBuilderUtils.buildUrl(href, { q: input.loadBalancer, reg: input.region, acct: input.account });
  }
}

class ProjectsUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    return $state.href('home.project.dashboard', { project: input.name }, { inherit: false });
  }
}

class SecurityGroupsUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    const href: string = $state.href('home.firewallDetails', {
      accountId: input.account,
      region: input.region,
      name: input.name,
      vpcId: input.vpcId,
      provider: input.provider,
    });

    return UrlBuilderUtils.buildUrl(href);
  }
}

class ServerGroupsUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    const baseName: string = input.project ? 'project' : 'applications';
    const href: string = $state.href(
      `home.${baseName}.application.insight.clusters.serverGroup`,
      {
        application: input.application,
        accountId: input.account,
        region: input.region,
        serverGroup: input.serverGroup,
        provider: input.provider,
        project: input.project,
      },
      { inherit: false },
    );

    return UrlBuilderUtils.buildUrl(href, { q: input.serverGroup, acct: input.account, reg: input.region });
  }
}

class TaskUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    return $state.href(
      'home.applications.application.tasks.taskDetails',
      {
        application: input.application,
        taskId: input.taskId,
      },
      { inherit: false },
    );
  }
}

class TasksUrlBuilder implements IUrlBuilder {
  public build(input: IUrlBuilderInput, $state: StateService) {
    return $state.href(
      'home.applications.application.tasks',
      {
        application: input.application,
      },
      { inherit: false },
    );
  }
}

export class UrlBuilder {
  private createCloneTask(task: ITask): string | boolean {
    const regionAndName: any = task.getValueFor('deploy.server.groups');
    const account: string = task.getValueFor('deploy.account.name');

    let result: string | boolean;
    if (!regionAndName || !Object.keys(regionAndName)[0]) {
      result = false;
    } else {
      const regions: string[] = Object.keys(regionAndName);
      const region: string = regions[0];
      const asgName: string = regionAndName[region][0];
      if (!asgName) {
        result = false;
      } else if (!asgName.match(NameUtils.VERSION_PATTERN)) {
        result = false;
      } else {
        result = this.$state.href('home.applications.application.insight.clusters.serverGroup', {
          application: asgName.split('-')[0],
          cluster: asgName.replace(NameUtils.VERSION_PATTERN, ''),
          account,
          accountId: account,
          region: regions,
          serverGroup: asgName,
          q: asgName,
        });
      }
    }

    return result;
  }

  private asgTask(task: ITask): string | boolean {
    const asgName: string = task.getValueFor('asgName');
    const account: string = task.getValueFor('credentials');

    let result: string | boolean;
    if (!asgName) {
      result = false;
    } else if (!asgName.match(NameUtils.VERSION_PATTERN)) {
      result = '/';
    } else {
      result = this.$state.href('home.applications.application.insight.clusters.serverGroup', {
        application: asgName.split('-')[0],
        cluster: asgName.replace(NameUtils.VERSION_PATTERN, ''),
        account,
        accountId: account,
        region: task.getValueFor('regions')[0],
        serverGroup: asgName,
      });
    }

    return result;
  }

  constructor(private $state: StateService) {}

  public static buildFromMetadata(input: IUrlBuilderInput) {
    const builder: IUrlBuilder = Registry.urlBuilder.getBuilder(input.type);
    let result: string;
    if (builder) {
      result = builder.build(input, ReactInjector.$state);
    } else {
      result = '/';
    }

    return result;
  }

  public buildFromTask(task: ITask): string | boolean {
    const description: string = task.name || '';
    function contains(str: string): boolean {
      return description.includes(str);
    }

    let result: string | boolean;
    switch (true) {
      case contains('Destroy Server Group'):
        result = false;
        break;
      case contains('Disable Server Group'):
        result = this.asgTask(task);
        break;
      case contains('Enable Server Group'):
        result = this.asgTask(task);
        break;
      case contains('Resize Server Group'):
        result = this.asgTask(task);
        break;
      case contains('Create Cloned Server Group'):
        result = this.createCloneTask(task);
        break;
      case contains('Create New Server Group'):
        result = this.createCloneTask(task);
        break;
      default:
        result = false;
    }

    return result;
  }
}

Registry.urlBuilder.register('applications', new ApplicationsUrlBuilder());
Registry.urlBuilder.register('clusters', new ClustersUrlBuilder());
Registry.urlBuilder.register('instances', new InstancesUrlBuilder());
Registry.urlBuilder.register('loadBalancers', new LoadBalancersUrlBuilder());
Registry.urlBuilder.register('projects', new ProjectsUrlBuilder());
Registry.urlBuilder.register('securityGroups', new SecurityGroupsUrlBuilder());
Registry.urlBuilder.register('serverGroups', new ServerGroupsUrlBuilder());
Registry.urlBuilder.register('task', new TaskUrlBuilder());
Registry.urlBuilder.register('tasks', new TasksUrlBuilder());
