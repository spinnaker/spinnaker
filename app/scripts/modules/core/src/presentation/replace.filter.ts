import { ILogService, isDefined, module } from 'angular';

replace.$inject = ['$log'];
function replace($log: ILogService): Function {
  return (str: string, regExStr: string, replaceValue: string) => {
    if (!isDefined(regExStr)) {
      $log.debug(`Did not supply regex string for 'replace' filter.`);
      return str;
    } else if (!isDefined(replaceValue)) {
      $log.debug(`Did not supply replacement value for 'replace' filter.`);
      return str;
    } else {
      return str.replace(new RegExp(regExStr, 'g'), replaceValue);
    }
  };
}

export const REPLACE_FILTER = 'spinnaker.core.replace.filter';
module(REPLACE_FILTER, []).filter('replace', replace);
