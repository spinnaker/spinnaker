import { useCallback, useState } from 'react';

export const useForceUpdate = () => {
  const [, setToggle] = useState(true);

  return useCallback(() => {
    setToggle((toggle) => !toggle);
  }, []);
};
