import type { IComponentController, IComponentOptions, IOnChangesObject, IRootElementService } from 'angular';
import React from 'react';
import ReactDOM from 'react-dom';

import { SpinErrorBoundary } from '../presentation/SpinErrorBoundary';

export function angularComponentFromReact<P>(
  Component: React.ComponentType<P>,
  componentName: string,
  propNames: string[] = [],
): IComponentOptions {
  class ReactComponentController implements IComponentController {
    public static $inject = ['$element'];
    private linked = false;

    public constructor(private $element: IRootElementService) {}

    public $postLink(): void {
      this.linked = true;
      this.renderReactComponent();
    }

    public $onChanges(_changes: IOnChangesObject): void {
      if (this.linked) {
        this.renderReactComponent();
      }
    }

    public $onDestroy(): void {
      ReactDOM.unmountComponentAtNode(this.$element[0]);
    }

    private renderReactComponent(): void {
      const props = propNames.reduce((acc, propName) => ({ ...acc, [propName]: (this as any)[propName] }), {} as P);
      ReactDOM.render(
        <SpinErrorBoundary category={componentName}>{React.createElement(Component, props)}</SpinErrorBoundary>,
        this.$element[0],
      );
    }
  }

  return {
    bindings: propNames.reduce((acc, propName) => ({ ...acc, [propName]: '<' }), {}),
    controller: ReactComponentController,
  };
}
