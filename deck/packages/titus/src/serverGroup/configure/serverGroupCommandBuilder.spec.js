import { TitusProviderSettings } from '../../titus.settings';
import { TitusServerGroupCommandBuilder } from './ServerGroupCommandBuilder';

describe('TitusServerGroupCommandBuilder', function () {
  afterEach(TitusProviderSettings.resetToOriginal);

  describe('buildNewServerGroupCommand', function () {
    it('initializes to default values', async function () {
      TitusProviderSettings.defaults.iamProfile = '{{application}}InstanceProfile';

      const command = await TitusServerGroupCommandBuilder.buildNewServerGroupCommand({ name: 'titusApp' });

      expect(command.iamProfile).toBe('titusAppInstanceProfile');
      expect(command.viewState.mode).toBe('create');
    });
  });

  describe('buildServerGroupCommandFromExisting', function () {
    it('sets iam profile if available otherwise uses the default', async function () {
      const baseServerGroup = {
        name: 'titusApp-test-test-v000',
        account: 'prod',
        region: 'us-west-1',
        cluster: 'titus-test-test',
        type: 'titus',
        cloudProvider: 'titus',
        iamProfile: 'titusAppInstanceProfile',
        resources: {},
        capacity: {},
        image: {},
      };

      const command = await TitusServerGroupCommandBuilder.buildServerGroupCommandFromExisting(
        { name: 'titusApp' },
        baseServerGroup,
      );

      expect(command.iamProfile).toBe('titusAppInstanceProfile');
      expect(command.viewState.mode).toBe('clone');
    });
  });
});
