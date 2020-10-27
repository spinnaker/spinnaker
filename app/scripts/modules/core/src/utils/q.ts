import { $q } from 'ngimport';
import { Observable } from 'rxjs';

export function toIPromise<T>(source: Observable<T>): PromiseLike<T> {
  return $q((resolve, reject) => {
    let value: any;
    source.subscribe(
      (x: T) => (value = x),
      (err: any) => reject(err),
      () => resolve(value),
    );
  });
}
