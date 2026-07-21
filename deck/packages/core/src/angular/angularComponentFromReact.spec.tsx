import React from 'react';
import ReactDOM from 'react-dom';

import { SpinErrorBoundary } from '../presentation/SpinErrorBoundary';
import { angularComponentFromReact } from './angularComponentFromReact';

describe('angularComponentFromReact', () => {
  it('creates one-way bindings for React props', () => {
    function Example() {
      return null;
    }

    const component = angularComponentFromReact(Example, 'example', ['value', 'onChange']);

    expect(component.bindings).toEqual({ value: '<', onChange: '<' });
  });

  it('renders on link, rerenders after link, and unmounts on destroy', () => {
    function Example() {
      return null;
    }

    const renderSpy = spyOn(ReactDOM, 'render');
    const unmountSpy = spyOn(ReactDOM, 'unmountComponentAtNode');
    const element = document.createElement('div');
    const component = angularComponentFromReact(Example, 'example', ['value']);
    const Controller = component.controller as any;
    const controller = new Controller([element]);
    controller.value = 'first';

    controller.$onChanges({});
    expect(renderSpy).not.toHaveBeenCalled();

    controller.$postLink();
    expect(renderSpy).toHaveBeenCalledTimes(1);
    expect((renderSpy.calls.mostRecent().args[0] as React.ReactElement).type).toBe(SpinErrorBoundary);

    controller.value = 'second';
    controller.$onChanges({});
    expect(renderSpy).toHaveBeenCalledTimes(2);

    controller.$onDestroy();
    expect(unmountSpy).toHaveBeenCalledWith(element);
  });
});
