import { AccountService } from '@spinnaker/core';

import { dcosServerGroupCommandBuilder } from './CommandBuilder';

describe('dcosServerGroupCommandBuilder', function () {
  beforeEach(function () {
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(Promise.resolve({ test: {} }));
  });

  describe('buildNewServerGroupCommand', function () {
    it('should initialize to default values', async function () {
      const command = await dcosServerGroupCommandBuilder.buildNewServerGroupCommand({
        name: 'dcosApp',
        accounts: ['test'],
      });
      expect(command.viewState.mode).toBe('create');
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('should use base server group otherwise use the default', async function () {
      var baseServerGroup = {};
      baseServerGroup.deployDescription = {
        account: 'test-account',
        region: 'cluster1/foo/bar',
        cluster: 'dcos-test-test',
        type: 'dcos',
        cloudProvider: 'dcos',
        resources: {},
        capacity: {},
        image: {},
      };

      const command = await dcosServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'dcosApp' },
        baseServerGroup,
      );

      expect(command.viewState.mode).toBe('clone');
    });
  });
});
