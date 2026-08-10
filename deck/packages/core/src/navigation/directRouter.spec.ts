import { UIRouterReact } from '@uirouter/react';

import { getDirectRouter, setDirectRouter } from './directRouter';

describe('directRouter', () => {
  afterEach(() => setDirectRouter(null));

  it('exposes the active direct UI Router instance', () => {
    const router = new UIRouterReact();

    setDirectRouter(router);

    expect(getDirectRouter()).toBe(router);
  });
});
