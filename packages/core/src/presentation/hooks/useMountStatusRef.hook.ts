import { useEffect, useRef } from 'react';

export function useMountStatusRef() {
  const mountStatusRef = useRef<'FIRST_RENDER' | 'MOUNTED' | 'UNMOUNTED'>('FIRST_RENDER');
  useEffect(() => {
    mountStatusRef.current = 'MOUNTED';
    return () => (mountStatusRef.current = 'UNMOUNTED');
  });
  return mountStatusRef;
}
