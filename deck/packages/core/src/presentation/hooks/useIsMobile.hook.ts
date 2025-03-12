import * as React from 'react';

import { useEventListener } from './useEventListener.hook';

const { useState } = React;

/////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////
///                  WARNING: EXPERIMENTAL                    ///
/// The details of this implementation (and its API contract) ///
///          may change in the foreseeable future             ///
/////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////

const BREAKPOINT_MOBILE = 1024;

export const useIsMobile = () => {
  const [isMobile, setIsMobile] = useState(window.innerWidth <= BREAKPOINT_MOBILE);

  useEventListener(window, 'resize', () => {
    const newValue = window.innerWidth <= BREAKPOINT_MOBILE;
    if (newValue !== isMobile) {
      setIsMobile(newValue);
    }
  });

  return isMobile;
};
