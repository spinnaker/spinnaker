import { createNativePromiseService } from './nativePromiseService';

describe('createNativePromiseService', () => {
  it('creates a native promise from a resolver', async () => {
    const promiseService = createNativePromiseService();

    await expectAsync(
      promiseService<string>((resolve) => resolve('resolved')),
    ).toBeResolvedTo('resolved');
  });

  it('creates a deferred native promise', async () => {
    const deferred = createNativePromiseService().defer<string>();

    deferred.resolve('resolved');

    await expectAsync(deferred.promise as Promise<string>).toBeResolvedTo('resolved');
  });

  it('provides native promise collection and settlement helpers', async () => {
    const promiseService = createNativePromiseService();
    const error = new Error('rejected');

    await expectAsync(promiseService.all([Promise.resolve('one'), 'two']) as Promise<string[]>).toBeResolvedTo([
      'one',
      'two',
    ]);
    await expectAsync(promiseService.when('resolved') as Promise<string>).toBeResolvedTo('resolved');
    await expectAsync(promiseService.reject(error) as Promise<never>).toBeRejectedWith(error);
  });

  it('resolves keyed promise collections while preserving their inferred shape', async () => {
    const promiseService = createNativePromiseService();

    const keyed: PromiseLike<{ name: string; count: number }> = promiseService.all({
      name: Promise.resolve('resolved'),
      count: 2,
    });

    await expectAsync(keyed as Promise<{ name: string; count: number }>).toBeResolvedTo({
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

    await expectAsync(keyed as Promise<{ resolved: string; rejected: never }>).toBeRejectedWith(error);
  });
});
