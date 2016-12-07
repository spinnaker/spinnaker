import {flatMap, uniqBy, mergeWith} from 'lodash';
import { module } from 'angular';
import { APPLICATION_READ_SERVICE } from 'core/application/service/application.read.service';
import { Application } from 'core/application/application.model';
import {Scope} from '../domain/scope.domain';
import {IImpactCounts} from '../domain/impactCounts.interface';

export let CATEGORY: any = {
  APPLICATIONS: 'Applications',
  CLUSTERS: 'Clusters',
  SERVER_GROUPS: 'Server Groups',
  INSTANCES: 'Instances',
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
    [CATEGORY.REGIONS]: this.buildScopeForRegions.bind(this),
    [CATEGORY.GLOBAL]: this.buildScopeForGlobal.bind(this)
  };

  private impactCountFnByCategory: any = {
    [CATEGORY.APPLICATIONS]: this.impactCountForApplication.bind(this),
    [CATEGORY.CLUSTERS]: this.impactCountForCluster.bind(this),
    [CATEGORY.SERVER_GROUPS]: this.impactCountForServerGroup.bind(this),
    [CATEGORY.INSTANCES]: this.impactCountForInstnace.bind(this),
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
  private buildScopeForGlobal(selected: any): Scope[] {
    let scope: Scope = new Scope();

    this.impactCountForGlobal()
      .then((counts) => {
        scope.instanceCounts = counts;
      });

    return [scope];
  }

  public impactCountForGlobal() {
    let countPromises = this.regions.map((region: any ) => {
      return this.fastPropertyReader.fetchImpactCountForScope({region: region.region});
    });

    return this.$q.all(countPromises)
      .then((countResults: any[]) => {
        let count = countResults.reduce((acc: number, result: any) => {
          return acc + Number.parseInt(result.count) ;
        }, 0 );
        return {up: count, total: count};
      });
  }



  /**
   * Builds Region level fast property
   */
  private buildScopeForRegions(selected: any): Scope[] {
    let scope = new Scope();
    scope.region = selected.region;
    this.impactCountForRegions(scope)
      .then((counts: IImpactCounts) => {
        scope.instanceCounts = counts;
      });

    return [scope];
  }

  public impactCountForRegions(scope: Scope): ng.IPromise<IImpactCounts> {
    return this.fastPropertyReader.fetchImpactCountForScope({region: scope.region})
      .then((results: any) => {
        return <IImpactCounts>{total: results.count, up: results.count};
      });
  }


  /**
   * Builds Application level fast property
   */
  private buildScopeListForApplication(selected: any, applicationDictionary: any): Scope[] {
    let application = applicationDictionary[selected.application];
    let scopes = flatMap(application.clusters, (cluster: any) => {
      return cluster.serverGroups.map((serverGroup: any) => {
        let scope = new Scope();
        scope.appId = selected.application;
        scope.region = serverGroup.region;
        return scope;
      });
    });

    let filteredScopes: Scope[] = <Scope[]>uniqBy(scopes, 'region');
    let scopesWithImpactCount: Scope[] = <Scope[]>filteredScopes.map((scope: Scope) => {
      let app = applicationDictionary[selected.application];
      scope.instanceCounts = this.impactCountForApplication(scope, app);
      return scope;
    });
    return scopesWithImpactCount;
  };

  public impactCountForApplication(scope: Scope, application: any): IImpactCounts {
    let serverGroupInstanceCounts = application.clusters
      .map((cluster: any) => cluster.serverGroups)
      .reduce((acc: any[], serverGroupList: any[]) => {
        serverGroupList.forEach((serverGroup) => {
          if (serverGroup.region === scope.region) {
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


  /**
   * Builds Cluster level fast property scope
   */
  private buildScopeForCluster(selected: any, applicationDictionary: any): Scope[] {
    let application = applicationDictionary[selected.application];
    let scope = new Scope();
    scope.appId = selected.application;
    scope.cluster = selected.cluster;
    scope.stack = selected.stack;
    scope.instanceCounts = this.impactCountForCluster(scope, application);
    return [scope];
  }

  public impactCountForCluster(scope: Scope, application: Application): IImpactCounts {
    let foundCluster: any = application.clusters
      .filter((cluster: any) => cluster.name === scope.cluster)
      .shift();

    return foundCluster ? foundCluster.instanceCounts : scope.instanceCounts;
  }


  /**
   * Builds Server Group level fast property scope
   */
  private buildScopeForServerGroup(selected: any, applicationDictionary: any): Scope[] {
    let application = applicationDictionary[selected.application];
    let scope = new Scope();
    scope.appId = selected.application;
    scope.cluster = selected.cluster;
    scope.stack = selected.stack;
    scope.region = selected.region;
    scope.asg = selected.serverGroup;
    scope.instanceCounts = this.impactCountForServerGroup(scope, application);
    return [scope];
  }

  public impactCountForServerGroup(scope: Scope, application: Application): IImpactCounts {
    let foundCluster = application.clusters
      .filter((cluster: any) => cluster.name === scope.cluster)
      .shift();

    if (foundCluster && foundCluster.serverGroups) {
      let serverGroup = foundCluster.serverGroups
        .filter((serverGroup: any) => serverGroup.name === scope.asg)
        .shift();

      return serverGroup ? serverGroup.instanceCounts : scope.instanceCounts;
    };

    return scope.instanceCounts;
  }


  /**
   * Builds Instance level fast property scope
   */
  private buildScopeForInstance(selected: any): Scope[] {
    let scope = new Scope();
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

  public buildScopeList(applicationDictionary: any, category: string, selected: any): Scope[]  {
    return this.scopeBuilderByCategory[category](selected, applicationDictionary);
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
        let category = scope.getCategory();
        return this.impactCountFnByCategory[category](scope, application);
      });
  }
}


export const FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE = 'spinnaker.netflix.fastproperty.scope.search.category.service';

module(FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE, [
  APPLICATION_READ_SERVICE,
])
  .service('fastPropertyScopeSearchCategoryService', FastPropertyScopeCategoryService);

