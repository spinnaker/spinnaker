import { useEffect } from 'react';
import { Observable } from 'rxjs';

/**
 * A react hook that subscribes to an rxjs observable on mount and calls the provided callback when
 * a new value is emitted. Unsubscribes on unmount or when the observable parameter changes.
 *
 * @param observable the rxjs observable to subscribe to
 * @param callback the function to call when the observable emits a value.
 * Passed the new value as its only argument.
 * @returns void
 */
export const useObservable = <T>(observable: Observable<T>, callback: (val: T) => void) => {
  useEffect(() => {
    if (!observable) {
      return undefined;
    }
    const subscription = observable.subscribe(callback);
    return () => subscription.unsubscribe();
  }, [observable]);
};
