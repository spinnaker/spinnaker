import React from 'react';

const { useEffect, useRef } = React;

export const usePrevious = <T>(value: T): T => {
  const ref = useRef<T>();
  useEffect(() => {
    ref.current = value;
  });
  return ref.current;
};
