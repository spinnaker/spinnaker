import {destroyPlatform} from '@angular/core';

import {DummyNg2Service} from 'core/upgrade/dummyNg2.service';

describe('test the angular 2 dummy service', () => {

  beforeEach(() => destroyPlatform());
  afterEach(() => destroyPlatform());

  let dummyService: DummyNg2Service;
  beforeEach(() => {
    dummyService = new DummyNg2Service();
  });

  it('should return the correct message', () => {
    expect(dummyService.getMessage()).toBe('Dummy NG2 Service');
  });
});
