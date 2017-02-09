import './fastPropetyScopeSearch.less';

import { CATEGORY_BUTTON_LIST_COMPONENT } from './categoryButtonList.component';

import {debounce, uniqWith, isEqual} from 'lodash';
import { module } from 'angular';
import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import { FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE, FastPropertyScopeCategoryService } from './fastPropertyScopeSearchCategory.service';
import {Scope} from '../domain/scope.domain';
import values = require('lodash/values');
import {Application} from 'core/application/application.model';
import {ICluster} from 'core/domain/ICluster';
import {ServerGroup} from 'core/domain/serverGroup';

export class FastPropertyScopeSearchComponentController implements ng.IComponentController {

  public query: string;
  public querying = false;
  public showSearchResults = false;
  public focussedResult: any;
  public selectedResult: any;
  public categories: any[];
  public scopeOptionsForDisplay: any;
  public env: string;
  public impactCount: string;
  public onScopeSelected: any;
  public applicationName: string;
  public regions: any;
  public applicationDictionary: any = {};
  public showNoImpactListForCategory: any = {};

  private search: any;

  static get $inject() {
    return [
      '$q',
      'infrastructureSearchService',
      'accountService',
      'fastPropertyScopeSearchCategoryService'
    ];
  }

  $onInit() {
    this.accountService.getAllAccountDetailsForProvider('aws')
      .then((accounts: any) => {
        let regions = accounts.reduce((acc: any, account: any) => {
          account.regions.forEach((region: any) => acc.add(region.name));
          return acc;
        }, new Set());

        this.regions = Array
          .from(regions)
          .map((region) => {
            return {
              displayName: region,
              region: region
            };
          });

        this.fastPropertyScopeSearchCategoryService.regions = this.regions;
      })
      .then(() => {
        if (this.applicationName && this.applicationName !== 'spinnakerfp') {
          this.query = this.applicationName;
          this.executeQuery();
        }
      });
  }

  constructor(
    private $q: ng.IQService,
    infrastructureSearchService: any,
    private accountService: AccountService,
    private fastPropertyScopeSearchCategoryService: FastPropertyScopeCategoryService) {
    this.search = infrastructureSearchService();
    this.executeQuery = debounce(this.executeQuery, 400);
  }


  public clearFilters() {
    this.query = '';
    this.showSearchResults = false;
  }

  /*
   * Select a category item from the list
   */
  public selectResult(category: string, selected: any) {
    this.selectedResult = selected;
    this.showSearchResults = false;
    this.scopeOptionsForDisplay = this.fastPropertyScopeSearchCategoryService.buildScopeList(this.categories, category, selected);
  }

  public displayResults() {
    this.showSearchResults = true;
  }

  public toggleNoInpactList(categoryName: string) {
    this.showNoImpactListForCategory[categoryName] = this.showNoImpactListForCategory[categoryName]
                                                    ? !this.showNoImpactListForCategory[categoryName]
                                                    : true;

  }


  /*
   * Query and build the category list
   */
  public dispatchQueryInput() {
    this.executeQuery();
  }

  private executeQuery() {
    this.querying = true;
    this.search
      .query(this.query)
      .then(this.excludeUnnecessaryCategories)
      .then(this.filterCategoriesByStartWithQuery)
      .then(this.addGlobalCategory)
      .then(this.addRegionCategory)
      .then(this.fetchApplicationInfo)
      .then(this.addStackCategory)
      .then(this.createScopesForEachCategoryResult)
      .then(this.doneQuerying);
  }

  /*
   * Filters
   */
  public noImpact(categoryScope: any) {
    return categoryScope.instanceCounts.up < 1;
  }

  private excludeUnnecessaryCategories = (results: any[]) => {
    return this.fastPropertyScopeSearchCategoryService.includeNeededCategories(results);
  };

  private filterCategoriesByStartWithQuery = (categories: any[]): any => {
    return categories.map((category: any) => {
      category.results = category.results.filter((r: any) => r.displayName.toLowerCase().startsWith(this.query.toLowerCase()));
      return category;
    });
  };

  public fetchApplicationInfo = (categories: any[]): any  => {
    let listOfPromises: ng.IPromise<any>[] = [];

    categories.forEach((category) => {
      category.results.forEach((item: any) => {
        if (item.application && !this.applicationDictionary[item.application] ) {
          this.applicationDictionary[item.application] = {}; // this is for the if-check
          listOfPromises.push(
            this.fastPropertyScopeSearchCategoryService.getApplicationByName(item.application)
              .then((application: any) => {
                this.applicationDictionary[item.application] = application;
              })
          );
        }
      });
    });

    return this.$q.all(listOfPromises).then(() => {
      return categories;
    });
  };

  private addGlobalCategory = (categories: any[]) => {
    categories.unshift({ order: 90, category: 'Global', results: [{displayName: 'Global'}] });
    return categories;
  };

  private addRegionCategory = (categories: any[]) => {
    categories.unshift({order: 80, category: 'Regions', results: this.regions});
    return categories;
  };

  private addStackCategory = (categories: any[]) => {
    let appsClusters = values(this.applicationDictionary).reduce((acc: any[], app: Application): any[] => {
      app.clusters.forEach((cluster: ICluster) => {
        cluster.serverGroups.forEach((serverGroup: ServerGroup) => {
          acc.push({
            region: serverGroup.region,
            application: app.name,
            cluster: serverGroup.cluster
          });
        });
      });
      return uniqWith(acc, isEqual);
    }, []);

    categories.unshift({order: 79, category: 'Stack', results: appsClusters });

    return categories;
  };

  private createScopesForEachCategoryResult = (categories: any[]) => {
    let categoriesWithScope = categories.map((category) => {
      let scopes = category.results.reduce((acc: any[], result: any) => {
        this.fastPropertyScopeSearchCategoryService.buildScopeList(this.applicationDictionary, category.category, result)
          .forEach((scope: any) => acc.push(scope));
        return acc;
      }, []);
      category.scopes = scopes;
      return category;
    });

    return categoriesWithScope;
  };

  private doneQuerying = (categories: any[]) => {
      this.categories = categories;
      this.displayResults();
      this.querying = false;
  };

  public selectScope(scopeOption: Scope) {
    this.onScopeSelected({ scopeOption: scopeOption });
  }

}

class FastPropertyScopeSearchComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./fastPropertyScopeSearch.component.html');
  public controller: any = FastPropertyScopeSearchComponentController;
  public bindings: any = {
    onScopeSelected: '&',
    applicationName: '='
  };
}

export const FAST_PROPERTY_SCOPE_SEARCH_COMPONENT = 'spinnaker.netflix.fastproperty.scope.search.component';

module(FAST_PROPERTY_SCOPE_SEARCH_COMPONENT, [
  require('core/search/infrastructure/infrastructureSearch.service'),
  require('../fastProperty.read.service'),
  ACCOUNT_SERVICE,
  FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE,
  CATEGORY_BUTTON_LIST_COMPONENT,
])
  .component('fastPropertyScopeSearchComponent', new FastPropertyScopeSearchComponent());
