import { createDeferred } from './deferred';

describe('createDeferred', () => {
  it('resolves its native promise', async () => {
    const deferred = createDeferred<string>();

    deferred.resolve('resolved');

    await expectAsync(deferred.promise).toBeResolvedTo('resolved');
  });

  it('rejects its native promise', async () => {
    const deferred = createDeferred<string>();
    const error = new Error('rejected');

    deferred.reject(error);

    await expectAsync(deferred.promise).toBeRejectedWith(error);
  });
});
