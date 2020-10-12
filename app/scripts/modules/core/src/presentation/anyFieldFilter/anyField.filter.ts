/**
 * From angular-ui-select demo: http://plnkr.co/edit/juqoNOt1z1Gb349XabQ2
 */
/**
 * AngularJS default filter with the following expression:
 * "person in people | filter: {name: $select.search, age: $select.search}"
 * performs a AND between 'name: $select.search' and 'age: $select.search'.
 * We want to perform a OR.
 */

import { module } from 'angular';

export function anyFieldFilter() {
  return function (items: any, props: any): any[] {
    let out: any[] = [];

    if (Array.isArray(items)) {
      items.forEach(function (item) {
        let itemMatches = false;

        const keys: any[] = Object.keys(props);
        for (const prop of keys) {
          const text: string = (props as any)[prop].toLowerCase();
          if (item[prop] && item[prop].toString().toLowerCase().includes(text)) {
            itemMatches = true;
            break;
          }
        }

        if (itemMatches) {
          out.push(item);
        }
      });
    } else {
      // Let the output be the input untouched
      out = items;
    }

    return out;
  };
}

export const ANY_FIELD_FILTER = 'spinnaker.core.presentation.anyFieldFilter';
module(ANY_FIELD_FILTER, []).filter('anyFieldFilter', anyFieldFilter);
