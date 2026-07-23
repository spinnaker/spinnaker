import { StyleguideRoute } from './StyleguideRoute';
import { getStyleguideState } from './styleguide.states';

describe('styleguide states', () => {
  it('uses React for the styleguide route', () => {
    const state = getStyleguideState();
    const view = state.views['main@'];

    expect(view).toEqual(jasmine.objectContaining({ component: StyleguideRoute, $type: 'react' }));
    expect(view.templateUrl).toBeUndefined();
    expect(view.controller).toBeUndefined();
    expect(view.controllerAs).toBeUndefined();
  });
});
