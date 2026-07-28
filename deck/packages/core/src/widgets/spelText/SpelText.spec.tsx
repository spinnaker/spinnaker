import { mount } from 'enzyme';
import React from 'react';
import { UIRouterReact } from '@uirouter/react';

import { SpelText } from './SpelText';
import { createDeckRuntime } from '../../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';

describe('SpelText', () => {
  it('does not throw during mount cleanup when input setup never ran', () => {
    const spelText = new SpelText({
      placeholder: '',
      value: '',
      onChange: () => undefined,
      pipeline: {} as any,
      docLink: false,
    });

    expect(() => spelText.componentWillUnmount()).not.toThrow();
  });

  it('uses the execution service owned by the runtime provider', () => {
    const runtime = createDeckRuntime(new UIRouterReact());
    try {
      const wrapper = mount(
        <DeckRuntimeContext.Provider value={runtime}>
          <SpelText placeholder="" value="" onChange={() => undefined} pipeline={{} as any} docLink={false} />
        </DeckRuntimeContext.Provider>,
      );
      const autocompleteService = (wrapper.find(SpelText).instance() as any).autocompleteService;

      expect(autocompleteService.executionService).toBe(runtime.services.executionService);
      wrapper.unmount();
    } finally {
      runtime.dispose();
    }
  });
});
