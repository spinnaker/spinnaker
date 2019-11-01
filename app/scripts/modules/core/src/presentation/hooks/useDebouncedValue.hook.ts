import { useEffect, useState } from 'react';

/**
 * A react hook which returns a debounced value
 *
 * @param value the immediate value
 * @param debounceMs: the debounce time, in ms
 */
export function useDebouncedValue<T>(value: T, debounceMs: number): [T, boolean] {
  const [debouncedValue, setDebouncedValue] = useState(value);
  const isDebouncing = value !== debouncedValue;
  useEffect(() => {
    const id = setTimeout(() => setDebouncedValue(value), debounceMs);
    return () => clearTimeout(id);
  }, [value, debounceMs]);

  return [debouncedValue, isDebouncing];
}
