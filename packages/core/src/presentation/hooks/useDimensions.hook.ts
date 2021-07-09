import { debounce } from 'lodash';
import React from 'react';

const getElementDimensions = (ref: React.RefObject<HTMLElement>) =>
  ref.current ? { width: ref.current.offsetWidth, height: ref.current.offsetHeight } : { width: 0, height: 0 };

export const useDimensions = (
  ref: React.RefObject<HTMLElement>,
  {
    delay = 100,
    isActive = true,
  }: {
    delay?: number;
    isActive?: boolean;
  },
) => {
  const [dimension, setDimension] = React.useState(getElementDimensions(ref));

  React.useLayoutEffect(() => {
    const debouncedResizeHandler = debounce(
      () => {
        setDimension(getElementDimensions(ref));
      },
      delay,
      { leading: true },
    );

    if (isActive && ref.current) {
      const observer = new ResizeObserver(debouncedResizeHandler);
      observer.observe(ref.current);
      return () => observer.disconnect();
    } else {
      return () => {};
    }
  }, [delay, isActive, ref.current]);

  return dimension;
};
