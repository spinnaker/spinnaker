import type { ParamDeclaration, StateDeclaration, UrlRouter } from '@uirouter/core';
import type { ParamTypeDefinition, ReactViewDeclaration } from '@uirouter/react';
import { isEqual, isPlainObject } from 'lodash';

import type { DeckRuntimeServices } from '../bootstrap/DeckRuntimeServices';
import type { IFilterConfig } from '../filterModel/IFilterModel';
import { applyRootStateRegistrations } from './rootState.registration';
import type { StateHelper } from './stateHelper.provider';

import './navigation.less';

// Typescript kludge to widen interfaces so INestedState can support both react and angular views
export interface IReactHybridIntermediate extends StateDeclaration {
  children?: INestedState[];
  component?: any;
  $type?: string;
  views?: { [key: string]: any };
}

export interface INestedState extends IReactHybridIntermediate {
  children?: INestedState[];
  component?: React.ComponentType | string;
  views?: { [key: string]: ReactViewDeclaration | any };
}

export class StateConfigProvider {
  private root: INestedState = {
    name: 'home',
    abstract: true,
    params: {
      allowModalToStayOpen: { dynamic: true, value: null },
    },
    url: '?{debug:boolean}&{vis:query}&{trace:query}',
    dynamic: true,
    children: [],
  };

  constructor(
    private $urlRouterProvider: UrlRouter,
    private stateHelperProvider: StateHelper,
    public readonly runtimeServices: DeckRuntimeServices,
  ) {
    if (stateHelperProvider) {
      applyRootStateRegistrations(this);
    }
  }

  /**
   * Adds a root state, e.g. /applications, /projects, /infrastructure
   * @param child the state to add
   */
  public addToRootState(child: INestedState): void {
    const current = this.root.children.find((c) => c.name === child.name);
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
  public addRewriteRule(base: string | RegExp, replacement: string | Function) {
    this.$urlRouterProvider.when(base, replacement as any);
  }

  public buildDynamicParams(paramConfig: IFilterConfig[]): { [key: string]: ParamDeclaration | any } {
    return paramConfig.reduce((acc: any, p) => {
      const param = p.param || p.model;
      acc[param] = {
        type: p.type || 'string',
        dynamic: true,
      };
      if (p.array) {
        acc[param].array = true;
      }
      return acc;
    }, {});
  }

  public paramsToQuery(paramConfig: IFilterConfig[]): string {
    return paramConfig.map((p) => p.param || p.model).join('&');
  }
}

export const trueKeyObjectParamType: ParamTypeDefinition = {
  decode: (val: string) => {
    if (val) {
      const r: any = {};
      val
        .split(',')
        .map((k) => k.replace(/%2c/g, ','))
        .forEach((k) => (r[k] = true));
      return r;
    }
    return {};
  },
  encode: (val: any) => {
    if (val) {
      const r = Object.keys(val).filter((k) => val[k]);
      return r.length
        ? r
            .sort()
            .map((k) => k.replace(/,/g, '%2c'))
            .join(',')
        : null;
    }
    return null;
  },
  equals: (a: any, b: any) => isEqual(a, b),
  is: (val: any) => isPlainObject(val),
};

export const inverseBooleanParamType: ParamTypeDefinition = {
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

export const booleanParamType: ParamTypeDefinition = {
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

export const sortKeyParamType: ParamTypeDefinition = {
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
  is: (val: any) => isPlainObject(val),
};

export const STATE_CONFIG_PROVIDER = 'spinnaker.core.navigation.state.config.provider';
