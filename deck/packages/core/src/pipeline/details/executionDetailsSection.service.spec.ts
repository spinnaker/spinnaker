import type { RawParams, StateService } from '@uirouter/core';
import { noop } from 'lodash';

import { ExecutionDetailsSectionService } from './executionDetailsSection.service';
import type { CancellableTimeout, CancellableTimeoutPromise } from '../../utils/cancellableTimeout';

type FlushableTimeout = CancellableTimeout & { flush: () => void };

function createFlushableTimeout(): FlushableTimeout {
  const queued: Array<{ promise: CancellableTimeoutPromise<unknown>; run: () => void }> = [];
  const timeout = (<T>(callback: () => T | PromiseLike<T>) => {
    let run: () => void;
    const promise = new Promise<T>((resolve, reject) => {
      run = () => {
        try {
          Promise.resolve(callback()).then(resolve, reject);
        } catch (error) {
          reject(error);
        }
      };
    }) as CancellableTimeoutPromise<T>;
    queued.push({ promise, run });
    return promise;
  }) as FlushableTimeout;
  timeout.cancel = (promise) => {
    const index = queued.findIndex((handle) => handle.promise === promise);
    if (index === -1) {
      return false;
    }
    queued.splice(index, 1);
    return true;
  };
  timeout.flush = () => queued.splice(0).forEach(({ run }) => run());
  timeout.dispose = () => queued.splice(0);
  return timeout;
}

describe('executionDetailsSectionService', function () {
  let $state: StateService, $stateParams: RawParams, timeout: FlushableTimeout, service: ExecutionDetailsSectionService;

  beforeEach(() => {
    $state = { includes: () => false, go: () => Promise.resolve() } as any;
    $stateParams = {};
    timeout = createFlushableTimeout();
    service = new ExecutionDetailsSectionService($stateParams, $state, timeout);
  });

  describe('synchronizeSection', () => {
    it('does nothing when state is not in execution details', function () {
      spyOn($state, 'includes').and.returnValue(false);
      spyOn($state, 'go');

      service.synchronizeSection(['a', 'b']);

      expect($state.includes).toHaveBeenCalledWith('**.execution');
      expect($state.go).not.toHaveBeenCalled();
    });

    it('reuses current section if still valid', function () {
      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      $stateParams.details = 'b';

      service.synchronizeSection(['a', 'b']);

      expect($state.includes).toHaveBeenCalledWith('**.execution');
      expect($state.go).not.toHaveBeenCalled();
    });

    it('replaces current section if not valid', function () {
      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      $stateParams.details = 'c';

      service.synchronizeSection(['a', 'b']);
      timeout.flush();
      expect($state.includes).toHaveBeenCalledWith('**.execution');
      expect($state.go).toHaveBeenCalledWith('.', { details: 'a' }, { location: 'replace' });
    });

    it('uses first section if none present in state params', function () {
      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      $stateParams.details = undefined;

      service.synchronizeSection(['a', 'b']);
      timeout.flush();
      expect($state.includes).toHaveBeenCalledWith('**.execution');
      expect($state.go).toHaveBeenCalledWith('.', { details: 'a' }, { location: 'replace' });
    });

    it('calls initialization after timeout', function () {
      let completed = false;
      const init = () => (completed = true);

      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      service.synchronizeSection(['a', 'b'], init);
      expect(completed).toBe(false);
      timeout.flush();
      expect(completed).toBe(true);
    });

    it('cancels prior initialization on second synchronization call', function () {
      let completed = false;
      const init = () => (completed = true);

      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      service.synchronizeSection(['a', 'b'], init);
      service.synchronizeSection(['a', 'b'], noop);
      timeout.flush();
      expect(completed).toBe(false);
    });
  });
});
