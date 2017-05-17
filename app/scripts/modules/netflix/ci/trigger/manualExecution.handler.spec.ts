import { IRootScopeService, mock } from 'angular';

import { IGitTrigger } from '@spinnaker/core';

import { NETFLIX_GIT_MANUAL_EXECUTION_HANDLER, NetflixGitManualExecutionHandler } from './manualExecution.handler';

describe('NetflixGitManualExecutionHandler', () => {

  let $scope: IRootScopeService;
  let executionHandler: NetflixGitManualExecutionHandler;

  beforeEach(mock.module(NETFLIX_GIT_MANUAL_EXECUTION_HANDLER));
  beforeEach(mock.inject(($rootScope: IRootScopeService,
                          netflixCiManualExecutionHandler: NetflixGitManualExecutionHandler) => {
    $scope = $rootScope;
    executionHandler = netflixCiManualExecutionHandler;
  }));

  it('should return the formatted label', (done) => {

    const trigger: IGitTrigger = {
      source: 'triggerSource',
      project: 'triggerProject',
      slug: 'triggerSlug',
      branch: 'triggerBranch',
      enabled: true,
      type: 'git'
    };

    executionHandler.formatLabel(trigger).then((label: string) => {
      expect(label).toBe(`(${trigger.source}) ${trigger.project}/${trigger.slug}`);
      done();
    });
    $scope.$digest();
  });
});
