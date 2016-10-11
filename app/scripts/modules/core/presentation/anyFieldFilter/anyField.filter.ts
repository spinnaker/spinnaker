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

const MODULE_NAME = 'spinnaker.core.presentation.anyFieldFilter';

export function anyFieldFilter() {
  return function(items: any, props: any): any[] {
    let out: any[] = [];

    if (angular.isArray(items)) {
      items.forEach(function (item) {
        let itemMatches = false;

        let keys: any[] = Object.keys(props);
        for (let i = 0; i < keys.length; i++) {
          let prop: any = keys[i];
          let text: string = (<any>props)[prop].toLowerCase();
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

module(MODULE_NAME, [])
  .filter('anyFieldFilter', anyFieldFilter);

export default MODULE_NAME;
