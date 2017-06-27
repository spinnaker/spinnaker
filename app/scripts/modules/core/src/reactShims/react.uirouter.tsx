import * as React from 'react';

import { module } from 'angular';
import { ReactViewDeclaration } from '@uirouter/react';
import { StateObject, UIRouter } from '@uirouter/core';
import './react.uirouter.css';

export const REACT_UIROUTER = 'spinnaker.core.react.uirouter';
const hybridModule = module(REACT_UIROUTER, ['ui.router']);

// UI-Router React 0.5.0 provides resolve data as a prop called `resolves`.
// This decorator spreads each individual resolved value to its _own prop_.
//
// e.g., instead of `props.resolves.myresolve` you can access `props.myresolve`
hybridModule.config(['$uiRouterProvider', (router: UIRouter) => {
  router.stateRegistry.decorator('views', (state: StateObject, parentDecorator) => {
    'ngNoInject';
    const views = parentDecorator(state);

    const BindResolvesToProps = (Component: any) => ({ resolves, ...props }: any) =>
        React.createElement(Component, { ...props, ...resolves });

    Object.keys(views).forEach(key => {
      const view: ReactViewDeclaration = views[key];
      if (view.$type === 'react' && typeof view.component === 'function') {
        view.component = BindResolvesToProps(view.component);
      }
    });

    return views;
  });
}]);
