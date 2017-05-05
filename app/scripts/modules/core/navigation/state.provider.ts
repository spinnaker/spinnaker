import { IServiceProvider, module } from 'angular';
import { Ng1StateDeclaration } from 'angular-ui-router';
import { isEqual, isPlainObject } from 'lodash';

import { STATE_HELPER, StateHelper } from './stateHelper.provider';
import { IFilterConfig } from '../filterModel/IFilterModel';
import { ParamDeclaration, UrlRouterProvider, UrlService } from 'angular-ui-router';

require('./navigation.less');

export interface INestedState extends Ng1StateDeclaration {
  children?: INestedState[];
}

export class StateConfigProvider implements IServiceProvider {

  private root: INestedState = {
    name: 'home',
    abstract: true,
    children: [],
  };

  constructor(private $urlRouterProvider: UrlRouterProvider, private stateHelperProvider: StateHelper) { 'ngInject'; }

  /**
   * Adds a root state, e.g. /applications, /projects, /infrastructure
   * @param child the state to add
   */
  public addToRootState(child: INestedState): void {
    const current = this.root.children.find(c => c.name === child.name);
    if (!current) {
      this.root.children.push(child);
    }
    this.setStates();
  }

  /**
   * registers any states that have been added as children to an already-registered state
   * (really just called internally by #addToRootState and by the ApplicationStateProvider methods)
   */
  public setStates(): void {
    this.stateHelperProvider.setNestedState(this.root);
  }

  /**
   * Configures a rewrite rule to automatically replace a base route
   * @param base, e.g. "/applications/{application}"
   * @param replacement, e.g. "/applications/{application}/clusters"
   */
  public addRewriteRule(base: string, replacement: string) {
    this.$urlRouterProvider.when(base, replacement);
  }

  public buildDynamicParams(paramConfig: IFilterConfig[]): ParamDeclaration {
    return paramConfig.reduce((acc: any, p) => {
      const param = p.param || p.model;
      acc[param] = {
        type: p.type || 'string',
        dynamic: true
      };
      return acc;
    }, {});
  }

  public paramsToQuery(paramConfig: IFilterConfig[]): string {
    return paramConfig.map(p => p.param || p.model).join('&');
  }

  public $get(): StateConfigProvider {
    return this;
  }
}

export const trueKeyObjectParamType = {
  decode: (val: string) => {
    if (val) {
      const r: any = {};
      val.split(',').forEach(k => r[k] = true);
      return r;
    }
    return {};
  },
  encode: (val: any) => {
    if (val) {
      const r = Object.keys(val).filter(k => val[k]);
      return r.length ? r.sort().join(',') : null;
    }
    return null;
  },
  equals: (a: any, b: any) => isEqual(a, b),
  is: (val: any) => isPlainObject(val)
};

export const inverseBooleanParamType = {
  decode: (val: string) => {
    if (val) {
      return val !== 'true';
    }
    return true;
  },
  encode: (val: any) => {
    return val ? null : 'true';
  },
  equals: (a: any, b: any) => a === b,
  is: () => true,
};

export const booleanParamType = {
  // as a string instead of a bit
  decode: (val: string) => {
    if (val) {
      return val === 'true';
    }
    return false;
  },
  encode: (val: any) => {
    return val ? 'true' : null;
  },
  equals: (a: any, b: any) => a === b,
  is: () => true,
};

export const sortKeyParamType = {
  decode: (val: string) => {
    return { key: val };
  },
  encode: (val: any) => {
    if (val) {
      return val.key;
    }
    return null;
  },
  equals: (a: any, b: any) => isEqual(a, b),
  is: (val: any) => isPlainObject(val)
};

export const STATE_CONFIG_PROVIDER = 'spinnaker.core.navigation.state.config.provider';
module(STATE_CONFIG_PROVIDER, [
  require('angular-ui-router').default,
  STATE_HELPER,
]).provider('stateConfig', StateConfigProvider)
  .config(($urlRouterProvider: UrlRouterProvider) => {
    $urlRouterProvider.otherwise('/');
    // Don't crash on trailing slashes
    $urlRouterProvider.when('/{path:.*}/', ['$match', ($match: any) => {
      return '/' + $match.path;
    }]);
  })
  .config(($urlServiceProvider: UrlService) => {
    $urlServiceProvider.config.type('trueKeyObject', trueKeyObjectParamType);
    $urlServiceProvider.config.type('inverse-boolean', inverseBooleanParamType);
    $urlServiceProvider.config.type('boolean', booleanParamType);
    $urlServiceProvider.config.type('sortKey', sortKeyParamType);
});
