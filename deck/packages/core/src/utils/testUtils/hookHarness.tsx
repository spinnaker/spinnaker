import { render } from '@testing-library/react';
import type { RenderOptions, RenderResult } from '@testing-library/react';
import React from 'react';

export function renderHookHarness<TProps extends object, TResult>(
  useHook: (props: TProps) => TResult,
  initialProps: TProps,
  options?: Omit<RenderOptions, 'queries'>,
): RenderResult & { result: { current: TResult }; rerenderHook: (props: TProps) => void } {
  const result = {} as { current: TResult };
  const Harness = (props: TProps): React.ReactElement | null => {
    result.current = useHook(props);
    return null;
  };
  const rendered = render(<Harness {...initialProps} />, options);
  return {
    ...rendered,
    result,
    rerenderHook: (props: TProps) => rendered.rerender(<Harness {...props} />),
  };
}
