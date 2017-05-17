'use strict';

describe('Jenkins Trigger: ExecutionHandler', function() {

  var $scope, handler;

  beforeEach(
    window.module(
      require('./jenkinsTrigger.module.js')
    )
  );

  beforeEach(window.inject(function($rootScope, jenkinsTriggerExecutionHandler) {
    $scope = $rootScope.$new();
    handler = jenkinsTriggerExecutionHandler;
  }));

  it('returns job and master as label', function () {
    let label = null;
    handler.formatLabel({job: 'a', master: 'b'}).then((result) => label = result);
    $scope.$digest();
    expect(label).toBe('(Jenkins) b: a');
  });

});
