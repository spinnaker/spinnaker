import React from 'react';

import { usePrevious } from './usePrevious.hook';

const { useEffect, useRef } = React;

const getContainerElement = () => document.querySelector('.spinnaker-container');

export const useContainerClassNames = (classNames: string[]) => {
  const previousClassNames = usePrevious(classNames);

  useEffect(() => {
    const containerElement = getContainerElement();
    if (!containerElement) {
      return undefined;
    }

    // If any of the classes in a previous `classNames` array are no longer present,
    // let's make sure they get cleaned up on the DOM
    if (previousClassNames) {
      const removedClassNames = previousClassNames.filter((className) => !classNames.includes(className));
      containerElement.classList.remove(...removedClassNames);
    }

    containerElement.classList.add(...classNames);
  }, [classNames.sort().join()]);

  // Take all the most recent classNames off at unmount. It'd be nice to do this in a function
  // returned from the other useEffect, but then we'd be thrashing class names off
  // and back onto the element every time the list of classes changes.
  const classNamesRef = useRef(classNames);
  classNamesRef.current = classNames;
  useEffect(() => {
    return () => {
      const containerElement = getContainerElement();
      if (!containerElement) {
        return undefined;
      }

      containerElement.classList.remove(...classNamesRef.current);
    };
  }, []);
};
