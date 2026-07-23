import { mock } from 'angular';

import { CORE_INSTANCE_INSTANCE_MODULE } from './instance.module';
import type { InstanceTypeService } from './instanceType.service';

describe('instance module', () => {
  beforeEach(mock.module(CORE_INSTANCE_INSTANCE_MODULE));

  it('registers instanceTypeService for server group configuration flows', () => {
    mock.inject((instanceTypeService: InstanceTypeService) => {
      expect(instanceTypeService).toBeDefined();
    });
  });
});
