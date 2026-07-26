import { createDeferred } from './deferred';

describe('createDeferred', () => {
  it('resolves its native promise', async () => {
    const deferred = createDeferred<string>();

    deferred.resolve('resolved');

    await expectAsync(deferred.promise).toBeResolvedTo('resolved');
  });

  it('assimilates a PromiseLike value passed to resolve', async () => {
    const deferred = createDeferred<string>();

    deferred.resolve(customThenable('thenable resolved'));

    await expectAsync(deferred.promise).toBeResolvedTo('thenable resolved');
  });

  it('rejects its native promise', async () => {
    const deferred = createDeferred<string>();
    const error = new Error('rejected');

    deferred.reject(error);

    await expectAsync(deferred.promise).toBeRejectedWith(error);
  });
});

function customThenable<T>(value: T): PromiseLike<T> {
  return {
    then: <TResult1 = T, TResult2 = never>(
      onfulfilled?: ((resolved: T) => TResult1 | PromiseLike<TResult1>) | null,
      onrejected?: ((reason: any) => TResult2 | PromiseLike<TResult2>) | null,
    ): PromiseLike<TResult1 | TResult2> => Promise.resolve(value).then(onfulfilled, onrejected),
  };
}
