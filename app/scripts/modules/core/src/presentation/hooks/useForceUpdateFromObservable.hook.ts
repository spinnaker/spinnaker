import { useEffect, useState } from 'react';
import { Observable } from 'rxjs';

export const useForceUpdateFromObservable = (observable: Observable<any>) => {
  const [, setTick] = useState(Date.now());

  useEffect(() => {
    const subscription = observable.subscribe(() => setTick(Date.now()));
    return () => subscription.unsubscribe();
  }, [observable]);
};
