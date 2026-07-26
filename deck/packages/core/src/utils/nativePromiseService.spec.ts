import { createNativePromiseService } from './nativePromiseService';

describe('createNativePromiseService', () => {
  it('returns only the neutral native promise operations', () => {
    const promiseService = createNativePromiseService();

    expect(typeof promiseService).toBe('object');
    expect(Object.keys(promiseService).sort()).toEqual(['all', 'reject', 'resolve']);
  });

  it('provides native promise collection and settlement helpers', async () => {
    const promiseService = createNativePromiseService();
    const error = new Error('rejected');

    await expectAsync(promiseService.all([Promise.resolve('one'), 'two'])).toBeResolvedTo(['one', 'two']);
    await expectAsync(promiseService.resolve('resolved')).toBeResolvedTo('resolved');
    await expectAsync(promiseService.reject(error)).toBeRejectedWith(error);
  });

  it('assimilates a custom thenable passed to resolve', async () => {
    const promiseService = createNativePromiseService();

    await expectAsync(promiseService.resolve(customThenable('thenable resolved'))).toBeResolvedTo('thenable resolved');
  });

  it('preserves tuple positions when resolving collections', async () => {
    const tuple: Promise<[string, number]> = createNativePromiseService().all([Promise.resolve('one'), 2] as const);

    await expectAsync(tuple).toBeResolvedTo(['one', 2]);
  });

  it('resolves keyed promise collections while preserving their inferred shape', async () => {
    const promiseService = createNativePromiseService();

    const keyed: Promise<{ name: string; count: number }> = promiseService.all({
      name: Promise.resolve('resolved'),
      count: 2,
    });

    await expectAsync(keyed).toBeResolvedTo({
      name: 'resolved',
      count: 2,
    });
  });

  it('rejects a keyed promise collection when one value rejects', async () => {
    const promiseService = createNativePromiseService();
    const error = new Error('keyed rejection');

    const keyed = promiseService.all({
      resolved: Promise.resolve('resolved'),
      rejected: Promise.reject(error),
    });

    await expectAsync(keyed).toBeRejectedWith(error);
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
