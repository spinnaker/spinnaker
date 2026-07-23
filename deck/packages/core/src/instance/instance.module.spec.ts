import { mock, module } from 'angular';

import { APPLICATION_STATE_PROVIDER } from '../application';
import { STATE_CONFIG_PROVIDER } from '../navigation';
import { CORE_INSTANCE_INSTANCE_MODULE } from './instance.module';
import type { InstanceTypeService } from './instanceType.service';

describe('instance module', () => {
  beforeEach(() => {
    module(APPLICATION_STATE_PROVIDER, []).provider('applicationState', function (this: any) {
      this.addInsightDetailState = () => undefined;
      this.$get = () => this;
    });
    module(STATE_CONFIG_PROVIDER, []).provider('stateConfig', function (this: any) {
      this.addToRootState = () => undefined;
      this.$get = () => this;
    });
  });

  beforeEach(mock.module(CORE_INSTANCE_INSTANCE_MODULE));

  it('registers instanceTypeService for server group configuration flows', () => {
    mock.inject((instanceTypeService: InstanceTypeService) => {
      expect(instanceTypeService).toBeDefined();
    });
  });
});
