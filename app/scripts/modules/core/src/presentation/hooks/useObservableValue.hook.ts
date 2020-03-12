import { useState } from 'react';
import { Observable } from 'rxjs';

import { useObservable } from './useObservable.hook';

/**
 * A react hook that returns the current value of an rxjs observable
 * and triggers a re-render when a new value is emitted.
 *
 * @param observable the rxjs observable to subscribe to
 * @param defaultValue the (optional) value to return before the observable
 * has emitted a value (for example, when the component is first mounting)
 * @returns the most recent value of the observable
 */
export const useObservableValue = <T>(observable: Observable<T>, defaultValue?: T) => {
  const [value, setValue] = useState<T>(defaultValue);
  useObservable(observable, setValue);
  return value;
};
