import {module, copy} from 'angular';
import {IStateProvider} from 'angular-ui-router';

import {INestedState} from './state.provider';

export class StateHelper implements ng.IServiceProvider {

  private registeredStates: string[] = [];

  static get $inject() { return ['$stateProvider']; }

  constructor(private $stateProvider: IStateProvider) {}

  public setNestedState(state: INestedState, keepOriginalNames = false) {
    const newState: INestedState = copy(state);
    if (!keepOriginalNames) {
      this.fixStateName(newState);
      this.fixStateViews(newState);
    }
    if (!this.registeredStates.includes(newState.name)) {
      this.registeredStates.push(newState.name);
      this.$stateProvider.state(newState);
    }

    if (newState.children && newState.children.length) {
      newState.children.forEach((childState: INestedState) => {
        childState.parent = newState.name;
        this.setNestedState(childState, keepOriginalNames);
      });
    }
  };

  private fixStateName(state: INestedState) {
    if (state.parent) {
      state.name = `${state.parent}.${state.name}`;
    }
  }

  private fixStateViews(state: INestedState) {
    const views = state.views || {},
        replaced: string[] = [];
    Object.keys(views).forEach((key) => {
      const relative: RegExpMatchArray = key.match('../');
      if (relative && relative.length && typeof state.parent === 'string') {
        const relativePath: string = state.parent.split('.').slice(0, -1 - relative.length).join('.') + '.';
        views[key.replace(/(..\/)+/, relativePath)] = views[key];
        replaced.push(key);
      }
    });
    replaced.forEach((key) => delete views[key]);
  }

  public $get(): StateHelper {
    return this;
  }
}

export const STATE_HELPER = 'spinnaker.core.navigation.stateHelper.provider';
module(STATE_HELPER, []).provider('stateHelper', StateHelper);
