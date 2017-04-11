import {compact, findIndex, uniqWith} from 'lodash';
import {module, ILogService, IComponentOptions, IComponentController} from 'angular';

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


class FastPropertyFilterSearchController implements IComponentController {
  public querying = false;
  public showSearchResults = false;
  public categories: any = [];
  public query: string;
  public filteredCategories: any[];
  public filters: any;
  public focussedResult: any;
  public createFilterTag: any;
  public showAllCategories = false;

  public properties: any;

  static get $inject() {
    return [
      '$element', '$log'
    ];
  }

  public constructor(
    private $element: JQuery,
    private $log: ILogService
  ) {}

  public $onInit(): void {
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
    const copy = this.filters.list.splice(0);
    const tagBody = {label: category, value: result};
    copy.push(this.createFilterTag(tagBody));
    this.filters.list = uniqWith(copy, (a: any, b: any) => a.label === b.label && a.value === b.value);
    this.$element.find('input').val('');
    this.showSearchResults = false;
    this.query = null;
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
}


class FastPropertyFilterSearchComponent implements IComponentOptions {

  public bindings: any = {
    'properties': '<',
    'filters': '=',
    'createFilterTag': '='
  };
  public controller: any = FastPropertyFilterSearchController;
  public controllerAs = 'fpFilter';
  public templateUrl: string = require('./fastPropertyFilterSearch.component.html');

}

export const FAST_PROPERTY_SEARCH_COMPONENT = 'spinnaker.netflix.fastPropertyFilterSearch.component';

module(FAST_PROPERTY_SEARCH_COMPONENT, [])
  .component('fastPropertyFilterSearch', new FastPropertyFilterSearchComponent());

