import type { StateParams, StateService } from '@uirouter/angularjs';
import { noop } from 'lodash';

import { ExecutionDetailsSectionService } from './executionDetailsSection.service';

describe('executionDetailsSectionService', function () {
  let $state: StateService,
    $stateParams: StateParams,
    $timeout: ng.ITimeoutService,
    service: ExecutionDetailsSectionService;

  beforeEach(() => {
    const queued: Array<{ callback: () => void }> = [];
    $state = { includes: () => false, go: () => Promise.resolve() } as any;
    $stateParams = {};
    $timeout = ((callback: () => void) => {
      const handle = { callback };
      queued.push(handle);
      return handle;
    }) as any;
    $timeout.cancel = (handle: any) => {
      const index = queued.indexOf(handle);
      if (index === -1) {
        return false;
      }
      queued.splice(index, 1);
      return true;
    };
    ($timeout as any).flush = () => queued.splice(0).forEach(({ callback }) => callback());
    service = new ExecutionDetailsSectionService($stateParams, $state, $timeout);
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
      $timeout.flush();
      expect($state.includes).toHaveBeenCalledWith('**.execution');
      expect($state.go).toHaveBeenCalledWith('.', { details: 'a' }, { location: 'replace' });
    });

    it('uses first section if none present in state params', function () {
      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      $stateParams.details = undefined;

      service.synchronizeSection(['a', 'b']);
      $timeout.flush();
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
      $timeout.flush();
      expect(completed).toBe(true);
    });

    it('cancels prior initialization on second synchronization call', function () {
      let completed = false;
      const init = () => (completed = true);

      spyOn($state, 'includes').and.returnValue(true);
      spyOn($state, 'go');

      service.synchronizeSection(['a', 'b'], init);
      service.synchronizeSection(['a', 'b'], noop);
      $timeout.flush();
      expect(completed).toBe(false);
    });
  });
});
