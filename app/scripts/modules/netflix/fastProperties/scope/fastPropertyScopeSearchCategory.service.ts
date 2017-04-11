import {flatMap, uniqBy, mergeWith, flatten} from 'lodash';
import { module } from 'angular';

import { APPLICATION_READ_SERVICE } from 'core/application/service/application.read.service';
import { FAST_PROPERTY_READ_SERVICE } from '../fastProperty.read.service';
import { Application } from 'core/application/application.model';
import {Scope} from '../domain/scope.domain';
import {IImpactCounts} from '../domain/impactCounts.interface';
import {ICluster} from 'core/domain/ICluster';
import {ServerGroup} from 'core/domain/serverGroup';

export let CATEGORY: any = {
  APPLICATIONS: 'Applications',
  CLUSTERS: 'Clusters',
  SERVER_GROUPS: 'Server Groups',
  INSTANCES: 'Instances',
  STACK: 'Stack',
  REGIONS: 'Regions',
  GLOBAL: 'Global',
};


export class FastPropertyScopeCategoryService {
  public regions: string[];

  private scopeBuilderByCategory: any = {
    [CATEGORY.APPLICATIONS]: this.buildScopeListForApplication.bind(this),
    [CATEGORY.CLUSTERS]: this.buildScopeForCluster.bind(this),
    [CATEGORY.SERVER_GROUPS]: this.buildScopeForServerGroup.bind(this),
    [CATEGORY.INSTANCES]: this.buildScopeForInstance.bind(this),
    [CATEGORY.STACK]: this.buildScopeForStack.bind(this),
    [CATEGORY.REGIONS]: this.buildScopeForRegions.bind(this),
    [CATEGORY.GLOBAL]: this.buildScopeForGlobal.bind(this)
  };

  private impactCountFnByCategory: any = {
    [CATEGORY.APPLICATIONS]: this.impactCountForApplication.bind(this),
    [CATEGORY.CLUSTERS]: this.impactCountForCluster.bind(this),
    [CATEGORY.SERVER_GROUPS]: this.impactCountForServerGroup.bind(this),
    [CATEGORY.INSTANCES]: this.impactCountForInstnace.bind(this),
    [CATEGORY.STACK]: this.impactCountForStack.bind(this),
    [CATEGORY.REGIONS]: this.impactCountForRegions.bind(this),
    [CATEGORY.GLOBAL]: this.impactCountForGlobal.bind(this)
  };

  static get $inject() {
    return [
      '$q',
      'applicationReader',
      'fastPropertyReader'
    ];
  }

  constructor(private $q: ng.IQService,
              private applicationReader: any,
              private fastPropertyReader: any) {}


  /**
   * Builds Global level fast property
   */
  private buildScopeForGlobal(): Scope[] {
    const scope: Scope = new Scope();

    this.impactCountForGlobal()
      .then((counts) => {
        scope.instanceCounts = counts;
      });

    return [scope];
  }

  public impactCountForGlobal() {
    const countPromises = this.regions.map((region: any ) => {
      return this.fastPropertyReader.fetchImpactCountForScope({region: region.region});
    });

    return this.$q.all(countPromises)
      .then((countResults: any[]) => {
        const count = countResults.reduce((acc: number, result: any) => {
          return acc + Number.parseInt(result.count) ;
        }, 0 );
        return {up: count, total: count};
      });
  }



  /**
   * Builds Region level fast property
   */
  private buildScopeForRegions(selected: any): Scope[] {
    const scope = new Scope();
    scope.region = selected.region;
    this.impactCountForRegions(scope)
      .then((counts: IImpactCounts) => {
        scope.instanceCounts = counts;
      });

    return [scope];
  }

  public impactCountForRegions(scope: Scope): ng.IPromise<IImpactCounts> {
    return this.fastPropertyReader.fetchImpactCountForScope(scope)
      .then((results: any) => {
        return <IImpactCounts>{total: parseInt(results.count, 10), up: parseInt(results.count, 10)};
      });
  }


  /**
   * Builds Application level fast property
   */
  private buildScopeListForApplication(selected: any, applicationDictionary: any, env: string): Scope[] {
    const application = applicationDictionary[selected.application];
    const scopes = flatMap(application.clusters, (cluster: any) => {
      return cluster.serverGroups.map((serverGroup: any) => {
        const scope = new Scope();
        scope.appId = selected.application;
        scope.region = serverGroup.region;
        return scope;
      });
    });

    const filteredScopes: Scope[] = <Scope[]>uniqBy(scopes, 'region');
    const scopesWithImpactCount: Scope[] = <Scope[]>filteredScopes.map((scope: Scope) => {
      const app = applicationDictionary[selected.application];
      scope.instanceCounts = this.impactCountForApplication(scope, app, env);
      return scope;
    });


    const appOnlyScope = new Scope();
    appOnlyScope.appId = selected.application;
    appOnlyScope.instanceCounts = this.impactCountForApplication(appOnlyScope, application, env);

    return flatten([scopesWithImpactCount, appOnlyScope]);
  };

  public impactCountForApplication(scope: Scope, application: any, env: string): IImpactCounts {
    const serverGroupInstanceCounts = application.clusters
      .map((cluster: any) => cluster.serverGroups)
      .reduce((acc: any[], serverGroupList: any[]) => {
        serverGroupList.forEach((serverGroup) => {
          if ( this.scopeEnvMatchesAccount(env, serverGroup.account) &&
            ( (scope.region && serverGroup.region === scope.region) || (scope.appId && !scope.region) ) ) {
            acc.push(serverGroup.instanceCounts);
          };
        });
        return acc;
      }, [])
      .reduce((acc: any, counts: any) => {
        return mergeWith(acc, counts, (a: number, b: number) =>  a + b );
      }, scope.instanceCounts);

    return serverGroupInstanceCounts;
  }

  public scopeEnvMatchesAccount(env: string, account: string) {
    return env === account || account.includes(env) || (env === 'prod' && !account.includes('test'));
  }


  /**
   * Builds Cluster level fast property scope
   */
  private buildScopeForCluster(selected: any, applicationDictionary: any, env: string): Scope[] {
    const application = applicationDictionary[selected.application];
    const scope = new Scope();
    scope.appId = selected.application;
    scope.cluster = selected.cluster;
    scope.stack = selected.stack;
    scope.instanceCounts = this.impactCountForCluster(scope, application, env);
    return [scope];
  }

  public impactCountForCluster(scope: Scope, application: Application, env: string): IImpactCounts {
    const foundCluster: any = application.clusters
      .filter((cluster: any) => cluster.name === scope.cluster && this.scopeEnvMatchesAccount(env, cluster.account))
      .shift();

    return foundCluster ? foundCluster.instanceCounts : scope.instanceCounts;
  }


  /**
   * Builds Server Group level fast property scope
   */
  private buildScopeForServerGroup(selected: any, applicationDictionary: any, env: string): Scope[] {
    const application = applicationDictionary[selected.application];
    const scope = new Scope();
    scope.appId = selected.application;
    scope.cluster = selected.cluster;
    scope.stack = selected.stack;
    scope.region = selected.region;
    scope.asg = selected.serverGroup;
    scope.instanceCounts = this.impactCountForServerGroup(scope, application, env);
    return [scope];
  }

  public impactCountForServerGroup(scope: Scope, application: Application, env: string): IImpactCounts {
    const foundCluster = application.clusters
      .filter((cluster: any) => cluster.name === scope.cluster && this.scopeEnvMatchesAccount(env, cluster.account))
      .shift();

    if (foundCluster && foundCluster.serverGroups) {
      const serverGroup = foundCluster.serverGroups
        .filter((sg: ServerGroup) => sg.name === scope.asg)
        .shift();

      return serverGroup ? serverGroup.instanceCounts : scope.instanceCounts;
    }

    return scope.instanceCounts;
  }

  /*
   * Build Stack Group level fast property scope
   */
  private buildScopeForStack(selected: any, applicationDictionary: any, env: string): Scope[] {
    const application = applicationDictionary[selected.application];
    const scope = new Scope();
    scope.region = selected.region;
    scope.appId = selected.application;
    scope.stack = selected.stack || this.getStackFromClusterName(selected.cluster);
    scope.instanceCounts = this.impactCountForStack(scope, application, env);
    return [scope];
  }

  public impactCountForStack(scope: Scope, application: Application, env: string): IImpactCounts {
    const foundClustersWithStack = application.clusters
      .filter((cluster: ICluster) =>  this.scopeEnvMatchesAccount(env, cluster.account) && this.getStackFromClusterName(cluster.name) === scope.stack);

    return foundClustersWithStack.reduce((acc: any, cluster: any) => {
      return mergeWith(acc, cluster.instanceCounts, (a: number, b: number) =>  a + b );
    }, scope.instanceCounts);
  }

  public getStackFromClusterName(clusterName: string ): string {
    const nameStackDetails = clusterName.split('-');
    return nameStackDetails.length > 1 ? nameStackDetails[1] : '';
  }

  /**
   * Builds Instance level fast property scope
   */
  private buildScopeForInstance(selected: any): Scope[] {
    const scope = new Scope();
    scope.appId = selected.application;
    scope.cluster = selected.cluster;
    scope.region = selected.region;
    scope.asg = selected.serverGroup;
    scope.serverId = selected.instanceId;
    scope.instanceCounts = this.impactCountForInstnace();
    return [scope];
  }

  private impactCountForInstnace(): IImpactCounts {
    return {up: 1}; // hard code it, assume the instance is up if not ¯\_(ツ)_/¯
  }

  public includeNeededCategories(results: any[]): any[] {
    const needed = ['Applications', 'Clusters', 'Server Groups', 'Instances'];
    return results.filter((category: any) => {
      return needed.includes(category.category) && category.results.length;
    });
  }

  public buildScopeList(applicationDictionary: any, category: string, selected: any, env: string): Scope[]  {
    return this.scopeBuilderByCategory[category](selected, applicationDictionary, env);
  }


  public getApplicationByName(applicationName: string): ng.IPromise<Application> {
    return this.applicationReader.getApplication(applicationName)
      .then((application: Application) => {
        return application.refresh(true)
          .then(() => {
            return application;
          });
      });
  }

  public getImpactForScope(scope: Scope): ng.IPromise<IImpactCounts> {
    return this.getApplicationByName(scope.appId)
      .then((application: any) => {
        return application;
      })
      .then((application: any) => {
        const category = scope.getCategory();
        return this.impactCountFnByCategory[category](scope, application, scope.env);
      });
  }
}


export const FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE = 'spinnaker.netflix.fastproperty.scope.search.category.service';

module(FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE, [
  APPLICATION_READ_SERVICE,
  FAST_PROPERTY_READ_SERVICE
])
  .service('fastPropertyScopeSearchCategoryService', FastPropertyScopeCategoryService);
