import { pushStateLocationPlugin, UIRouter } from '@uirouter/react';
import { mount } from 'enzyme';
import * as React from 'react';
import { act } from 'react-dom/test-utils';

// ----------------------------------------------------------------------------------
// Test Utilities
// ----------------------------------------------------------------------------------

/**
 * Resolve pending tasks scheduled by the component.
 */
const flush = () => new Promise((r) => setTimeout(r, 0));

/**
 * Create a minimal UIRouter environment so components can render RouteLinks, etc.
 */
export const wrapWithRouter = (node: React.ReactElement) => (
  <UIRouter plugins={[pushStateLocationPlugin]}>{node}</UIRouter>
);

/** Helper to DRY mounting + async flush + update. */
export async function mountAndFlush(element: React.ReactElement) {
  const wrapper = mount(wrapWithRouter(element));
  await act(async () => {
    await flush();
  });
  wrapper.update();
  return wrapper;
}
