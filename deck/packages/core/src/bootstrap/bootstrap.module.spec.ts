import { mock } from 'angular';
import UI_ROUTER from '@uirouter/angularjs';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import UI_SELECT from 'ui-select';

import { APPLICATION_BOOTSTRAP_MODULE } from './bootstrap.module';
import type { CacheInitializerService } from '../cache';

describe('application bootstrap module', () => {
  beforeEach(mock.module(UI_ROUTER, ANGULAR_UI_BOOTSTRAP, UI_SELECT, APPLICATION_BOOTSTRAP_MODULE));

  it('registers cacheInitializer for startup and server group configuration flows', () => {
    mock.inject((cacheInitializer: CacheInitializerService) => {
      expect(cacheInitializer).toBeDefined();
    });
  });
});
