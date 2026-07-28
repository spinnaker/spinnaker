import type { Observable } from 'rxjs';

export function toPromise<T>(source: Observable<T>): Promise<T> {
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
