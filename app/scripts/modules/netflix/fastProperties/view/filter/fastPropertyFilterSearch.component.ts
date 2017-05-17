import { IComponentController, IComponentOptions, ILogService, module } from 'angular';
import { StateParams, StateService } from 'angular-ui-router';
import { compact, findIndex, uniqWith } from 'lodash';
import { Subject } from 'rxjs/Subject';

import { IFilter, IFilterTag } from '@spinnaker/core';

interface IFastProperty {
  scope: IFastPropertyScope;
  key: string;
  value: string;
}

interface IFastPropertyScope {
  key: string;
  app: string;
  env: string;
  region: string;
  stack: string;
  cluster: string;
}

export const filterNames = ['substring', 'key', 'value', 'app', 'env', 'region', 'stack', 'cluster'];

class FastPropertyFilterSearchController implements IComponentController {
  public querying = false;
  public showSearchResults = false;
  public categories: any = [];
  public query: string;
  public filteredCategories: any[];
  public filters: IFilterTag[];
  public focussedResult: any;
  public showAllCategories = false;
  public filtersUpdatedStream: Subject<IFilterTag[]>;

  public properties: any;

  constructor (
    private $element: JQuery,
    private $log: ILogService,
    private $stateParams: StateParams,
    private $state: StateService,
  ) {
    'ngInject';
  }

  public $onInit(): void {
    const filterTags = this.paramsToTagList();
    const filters = uniqWith(filterTags, (a: IFilterTag, b: IFilterTag) => a.label === b.label && a.value === b.value);
    this.filters = filters;
    if (this.filtersUpdatedStream) {
      this.filtersUpdatedStream.next(filters);
    }
    this.setStateParams();
  }

  public $onChanges(): void {
    this.createFilterCategories(this.properties);
  }

  private displayAllCategories(): void {
    this.filteredCategories = this.categories;
    this.showSearchResults = true;
  }

  public displayResults(): void {
    if (this.query) {
      this.executeQuery();
    } else {
      this.displayAllCategories();
    }
  }

  private reset(): void {
    this.querying = false;
    this.query = null;
    this.showSearchResults = false;
    this.showAllCategories = false;
    this.focussedResult = null;
  }

  private hideResults(): void {
    this.showSearchResults = false;
    this.showAllCategories = false;
  }


  public tagAndClearFilter(category: string, result: string): void {
    const copy = this.filters.splice(0);
    const tagBody = {label: category, value: result};
    copy.push(this.createFilterTag(tagBody));
    this.filters.length = 0;
    this.filters.push(...uniqWith(copy, (a: any, b: any) => a.label === b.label && a.value === b.value));
    this.$element.find('input').val('');
    this.showSearchResults = false;
    this.query = null;
    if (this.filtersUpdatedStream) {
      this.filtersUpdatedStream.next(this.filters);
    }
    this.setStateParams();
  }


  public navigateResults(event: any) {
    if (event.which === 27) { // escape
      this.reset();
    }
    if (event.which === 9) { // tab - let it navigate automatically, but close menu if on the last result
      if (this.$element.find('ul.dropdown-menu').find('a').last().is(':focus')) {
        this.hideResults();
        return;
      }
    }
  };

  public dispatchQueryInput(event: any) {
    if (this.showSearchResults) {
      const code = event.which;

      if (code === 40) { // down
        return this.focusFirstSearchResult(event);
      }
      if (code === 38) { // up
        return this.focusLastSearchResult(event);
      }
      if (code === 13) {
        return;
      }
      if (code === 9) { // tab
        if (!event.shiftKey) {
          this.focusFirstSearchResult(event);
        }
        return;
      }
      if (code < 46 && code !== 8) { // bunch of control keys, except delete (46), and backspace (8)
        return;
      }
      if (code === 91 || code === 92 || code === 93) { // left + right command/window, select
        return;
      }
      if (code > 111 && code < 186) { // f keys, misc
        return;
      }
    }

    this.executeQuery();

  }

  public focusFirstSearchResult(event: any) {
    try {
      event.preventDefault();
      this.$element.find('ul.dropdown-menu').find('a').first().focus();
    } catch (e) {
      this.$log.debug(e);
    }
  }

  public searchFieldBlurred(blurEvent: any) {
    // if the target is outside the global search (e.g. shift+tab), hide the results
    if (!$.contains(this.$element.get(0), blurEvent.relatedTarget)) {
      this.hideResults();
    }
  }

  public createFilterTag(tag: IFilter): IFilterTag {
    if (tag) {
      return {
        label: tag.label,
        value: tag.value,
        clear: () => {
          this.filters = this.filters.filter(f => !(f.label === tag.label && f.value === tag.value));
          if (this.filtersUpdatedStream) {
            this.filtersUpdatedStream.next(this.filters);
          }
          this.setStateParams();
        }
      }
    }
    return null;
  }

  private focusLastSearchResult(event: any) {
    try {
      event.preventDefault();
      this.$element.find('ul.dropdown-menu').find('a').last().focus();
    } catch (e) {
      this.$log.debug(e);
    }
  }

  private executeQuery() {
    if (this.query) {
      this.querying = true;
      this.filteredCategories = compact(this.categories.map((category: any) => {
        const results: any[] =
          category.results.filter((result: string) => result.toLowerCase().includes(this.query.toLowerCase()));
        let result: any = null;
        if (results.length > 0) {
          result = {category: category.category, results: results};
        }
        return result;
      }));
      this.filteredCategories.splice(0, 0, {category: 'substring', results: [this.query]});
    }
    this.querying = false;
    this.showSearchResults = !!this.query;
  }


  private createFilterCategories(properties: IFastProperty[]) {
    this.categories = properties.reduce((acc: any, property: IFastProperty) => {
      const scope: IFastPropertyScope = property.scope;
      acc = this.addToList(acc, 'key', property.key );
      acc = this.addToList(acc, 'value', property.value);
      acc = this.addToList(acc, 'app', scope.app );
      acc = this.addToList(acc, 'env', scope.env );
      acc = this.addToList(acc, 'region', scope.region );
      acc = this.addToList(acc, 'stack', scope.stack);
      acc = this.addToList(acc, 'cluster', scope.cluster);
      return acc;
    }, []);
  }

  private addToList(acc: any[], scopeKey: string, scopeValue: string): any {
    if (scopeValue && scopeKey) {
      const categoryIndex = findIndex(acc, ['category', scopeKey]);
      if (categoryIndex > -1) {
        const categoryResults = acc[categoryIndex].results;
        if (categoryResults.indexOf(scopeValue) === -1) {
          categoryResults.push(scopeValue);
        }
      } else {
        acc.push({category: scopeKey, results: ['none', scopeValue]});
      }
    }
    return acc;
  }

  private paramsToTagList(): IFilterTag[] {
    const tagList = [] as IFilterTag[];
    filterNames.forEach(f => {
      if (this.$stateParams[f]) {
        (this.$stateParams[f] as string[]).forEach(v => {
          tagList.push(this.createFilterTag({label: f, value: v}));
        });
      }
    });
    return tagList;
  }

  private setStateParams(): void {
    const newFilters = filterNames.reduce((acc: any, filterName) => {
      acc[filterName] = this.filters.filter(f => f.label === filterName).map(f => f.value);
      return acc;
    }, {});
    this.$state.go('.', newFilters);
  }

}


export const fastPropertyFilterSearchComponent: IComponentOptions = {
  bindings: {
    properties: '<',
    filtersUpdatedStream: '<',
  },
  controller: FastPropertyFilterSearchController,
  controllerAs: 'fpFilter',
  templateUrl: require('./fastPropertyFilterSearch.component.html'),
};

export const FAST_PROPERTY_SEARCH_COMPONENT = 'spinnaker.netflix.fastPropertyFilterSearch.component';

module(FAST_PROPERTY_SEARCH_COMPONENT, [])
  .component('fastPropertyFilterSearch', fastPropertyFilterSearchComponent);

