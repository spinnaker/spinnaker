import { act } from '@testing-library/react';
import React from 'react';
import { Subject } from 'rxjs';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useForceUpdate } from '..';

describe('useForceUpdate', () => {
  it('should rerender when the mutation stream changes', () => {
    const stream = new Subject<void>();
    let renderCount = 0;
    const rendered = renderHookHarness(
      ({ mutationStream }) => {
        const forceUpdate = useForceUpdate();
        React.useEffect(() => {
          const subscription = mutationStream.subscribe(() => forceUpdate());
          return () => subscription.unsubscribe();
        });
        renderCount += 1;
        return renderCount;
      },
      { mutationStream: stream },
    );

    expect(rendered.result.current).toBe(1);
    act(() => stream.next());
    expect(rendered.result.current).toBe(2);
    act(() => stream.next());
    expect(rendered.result.current).toBe(3);

    rendered.unmount();
    act(() => stream.next());
    expect(renderCount).toBe(3);
  });
});
