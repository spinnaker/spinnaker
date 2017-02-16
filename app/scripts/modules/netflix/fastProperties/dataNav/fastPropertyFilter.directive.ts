import {flatten, uniq, uniqWith} from 'lodash';
import {module} from 'angular';
import { DirectiveFactory } from 'core/utils/tsDecorators/directiveFactoryDecorator';

@DirectiveFactory()
class FastPropertyFilterDirective implements ng.IDirective {

  scope: any = {
    'properties': '=',
    'filters': '=',
    'createFilterTag': '='
  };
  template = `<input type="search" class="form-control" placeholder="Filters: type '?'">`;
  restrict = 'E';
  fields: string[] = ['app', 'env', 'region', 'stack', 'cluster'];

  link(scope: any, el: any) {
    let input = el.children('input');

    let getScopeAttributeList = (scopeName: string) => {
      return ['none'].concat(<string[]> uniq(scope.properties.map((prop: any) => prop.scope[scopeName])));
    };

    let textcompleteComponents = this.fields.map((field) => {
      return {
        id: field,
        match: new RegExp(`${field}:(\\w*|\\s*)$`),
        index: 1,
        search: (term: string, callback: any) => {
          callback(getScopeAttributeList(field).filter((attr: string) => {
            return attr.includes(term);
          }));
        },
        replace: (attr: string): string => {
          let copy = scope.filters.list.splice(0);
          let tagBody = {label: field, value: attr};
          copy.push(scope.createFilterTag(tagBody));
          scope.filters.list = uniqWith(copy, (a: any, b: any) => a.label === b.label && a.value === b.value);
          return '';
        }
      };
    });

    input.textcomplete(flatten([
      textcompleteComponents,
      {
        match: /(\s*|\w*)\?(\s*|\w*|')$/,
        index: 2,
        search: (term: string, callback: any) => {
          callback($.map(this.fields, (word: string) => {
            return word.indexOf(term) > -1 ? word : null;
          }));
        },
        replace: (word: string) => {
          return `${word}:`;
        }
      },
      {
        match: /(^|\b)(\w{1,})$/,
        search: (term: string, callback: any): any => {
          callback($.map(this.fields, (word: string) => {
            return word.indexOf(term) > -1 ? word : null;
          }));
        },
        replace: (word: string): string => {
          return `${word}:`;
        }
      },

    ]));
  }
}

export const FAST_PROPERTY_FILTER_DIRECTIVE = 'spinnaker.netflix.fastPropertyFilter.directive';

module(FAST_PROPERTY_FILTER_DIRECTIVE, [])
  .directive('fastPropertyFilter', <any>FastPropertyFilterDirective);

