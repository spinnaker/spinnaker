import type { StateRegistry } from '@uirouter/core';
import { cloneDeep } from 'lodash';

import type { INestedState } from './state.provider';

export class StateHelper {
  private registeredStates: string[] = [];

  constructor(private $stateRegistryProvider: StateRegistry) {}

  public setNestedState(state: INestedState, keepOriginalNames = false) {
    const newState: INestedState = cloneDeep(state);
    if (!keepOriginalNames) {
      this.fixStateName(newState);
      this.fixStateViews(newState);
      newState.parent = null;
    }
    if (!this.registeredStates.includes(newState.name)) {
      this.registeredStates.push(newState.name);
      this.$stateRegistryProvider.register(newState as any);
    }

    if (newState.children && newState.children.length) {
      newState.children.forEach((childState: INestedState) => {
        childState.parent = newState.name;
        this.setNestedState(childState, keepOriginalNames);
      });
    }
  }

  private fixStateName(state: INestedState) {
    if (state.parent) {
      state.name = `${state.parent}.${state.name}`;
    }
  }

  private fixStateViews(state: INestedState) {
    const views = state.views || {};
    const replaced: string[] = [];
    Object.keys(views).forEach((key) => {
      const parent = typeof state.parent === 'string' ? state.parent : undefined;
      const viewName = this.resolveRelativeViewName(key, parent);
      if (viewName !== key) {
        views[viewName] = views[key];
        replaced.push(key);
      }
    });
    replaced.forEach((key) => delete views[key]);
  }

  private resolveRelativeViewName(key: string, parent: string | undefined): string {
    if (!key.includes('../') || typeof parent !== 'string') {
      return key;
    }

    const [viewName, target = ''] = key.split('@');
    if (target.includes('../')) {
      return `${viewName}@${this.resolveRelativeTarget(target, parent)}`;
    }

    const relative = viewName.match(/\.\.\//g);
    if (!relative) {
      return key;
    }

    const relativePath: string = parent.split('.').slice(0, -relative.length).join('.') + '.';
    return key.replace(/(..\/)+/, relativePath);
  }

  private resolveRelativeTarget(target: string, parent: string): string {
    const resolved = parent.split('.');
    target.split('/').forEach((part) => {
      if (!part || part === '.') {
        return;
      }

      if (part === '..') {
        resolved.pop();
        return;
      }

      if (resolved[resolved.length - 1] !== part) {
        resolved.push(part);
      }
    });

    return resolved.join('.');
  }
}

export const STATE_HELPER = 'spinnaker.core.navigation.stateHelper.provider';
