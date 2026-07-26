import type { Observable } from 'rxjs';

export function toIPromise<T>(source: Observable<T>): PromiseLike<T> {
  const promiseFactory = (resolve: (value: T) => void, reject: (reason?: any) => void) => {
    let value: any;
    source.subscribe(
      (x: T) => (value = x),
      (err: any) => reject(err),
      () => resolve(value),
    );
  };

  return new Promise(promiseFactory);
}
