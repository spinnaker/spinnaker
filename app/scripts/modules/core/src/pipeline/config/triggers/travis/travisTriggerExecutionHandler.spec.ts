import {mock, IScope, IRootScopeService} from 'angular';

import {TRAVIS_TRIGGER} from './travisTrigger.module';

describe('Travis Trigger: ExecutionHandler', () => {

  let $scope: IScope, handler: any;

  beforeEach(mock.module(TRAVIS_TRIGGER));

  beforeEach(mock.inject(($rootScope: IRootScopeService, travisTriggerExecutionHandler: any) => {
    $scope = $rootScope.$new();
    handler = travisTriggerExecutionHandler;
  }));

  it('returns job and master as label', () => {
    let label: string = null;
    handler.formatLabel({job: 'a', master: 'b'}).then((result: string) => label = result);
    $scope.$digest();
    expect(label).toBe('(Travis) b: a');
  });

});
