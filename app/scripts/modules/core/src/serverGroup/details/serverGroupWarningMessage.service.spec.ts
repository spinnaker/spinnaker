import { ServerGroupWarningMessageService } from './serverGroupWarningMessage.service';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { IServerGroup } from '../../domain';
import { Application } from '../../application/application.model';
import { IConfirmationModalParams } from '../../confirmationModal/confirmationModal.service';

describe('ServerGroupWarningMessageService', () => {
  let app: Application, serverGroup: IServerGroup;

  beforeEach(() => (app = ApplicationModelBuilder.createApplicationForTests('app')));

  describe('addDestroyWarningMessage', () => {
    it('leaves parameters unchanged when additional server groups exist in cluster', () => {
      serverGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 0, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [],
        name: 'foo-v000',
        region: 'us-east-1',
        type: 'a',
      };

      app.clusters = [
        {
          name: 'foo',
          account: 'test',
          cloudProvider: '',
          category: '',
          serverGroups: [
            serverGroup,
            {
              account: 'test',
              cloudProvider: 'aws',
              cluster: 'foo',
              instanceCounts: { up: 0, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
              instances: [],
              name: 'foo-v001',
              region: 'us-east-1',
              type: 'a',
            },
          ],
        },
      ];
      const params: IConfirmationModalParams = {};
      ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, params);
      expect(params.body).toBeUndefined();
    });

    it('adds a body to the parameters with cluster name, region, account when this is the last server group', () => {
      serverGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 0, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [],
        name: 'foo-v000',
        region: 'us-east-1',
        type: 'a',
      };

      app.clusters = [
        {
          name: 'foo',
          account: 'test',
          cloudProvider: '',
          category: '',
          serverGroups: [serverGroup],
        },
      ];
      const params: IConfirmationModalParams = {};
      ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, params);
      expect(params.body).toBeDefined();
      expect(params.body.includes('You are destroying the last Server Group in the Cluster')).toBe(true);
      expect(params.body.includes('test')).toBe(true);
      expect(params.body.includes('foo')).toBe(true);
      expect(params.body.includes('us-east-1')).toBe(true);
    });
  });

  describe('addDisableWarningMessage', () => {
    it('leaves parameters unchanged when server group has no instances', () => {
      serverGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 0, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [],
        name: 'foo-v000',
        region: 'us-east-1',
        type: 'a',
      };

      app.clusters = [
        {
          name: 'foo',
          account: 'test',
          cloudProvider: '',
          category: '',
          serverGroups: [serverGroup],
        },
      ];
      const params: IConfirmationModalParams = {};
      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, params);
      expect(params.body).toBeUndefined();
    });

    it('adds warning if there are any other instances, even if they are disabled', () => {
      serverGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 1, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [{ name: 'a', id: 'a', launchTime: 1, zone: 'b', health: null }],
        name: 'foo-v000',
        region: 'us-east-1',
        type: 'a',
      };
      const down: IServerGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 0, down: 1, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [{ name: 'a', id: 'a', launchTime: 1, zone: 'b', health: null }],
        name: 'foo-v001',
        region: 'us-east-1',
        type: 'a',
      };

      app.clusters = [
        {
          name: 'foo',
          account: 'test',
          cloudProvider: '',
          category: '',
          serverGroups: [serverGroup, down],
        },
      ];
      const params: IConfirmationModalParams = { account: 'prod' };
      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, params);
      expect(params.body).toBeDefined();
      expect(params.body.includes('<li>')).toBe(false);
      expect(params.textToVerify).toBe('0');
      expect(params.account).toBeUndefined();
    });

    it("adds warning if it's the last active ASG", () => {
      serverGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 1, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [{ name: 'a', id: 'a', launchTime: 1, zone: 'b', health: null }],
        name: 'foo-v000',
        region: 'us-east-1',
        type: 'a',
      };

      app.clusters = [
        {
          name: 'foo',
          account: 'test',
          cloudProvider: '',
          category: '',
          serverGroups: [serverGroup],
        },
      ];
      const params: IConfirmationModalParams = { account: 'prod' };
      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, params);
      expect(params.body).toBeDefined();
      expect(params.body.includes('<li>')).toBe(false);
      expect(params.textToVerify).toBe('0');
      expect(params.account).toBeUndefined();
    });

    it('adds remaining server groups to the body if they have up instances, removes account from params', () => {
      serverGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 1, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [{ name: 'a', id: 'a', launchTime: 1, zone: 'b', health: null }],
        name: 'foo-v000',
        region: 'us-east-1',
        type: 'a',
      };
      const omitted: IServerGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 0, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [],
        name: 'foo-v001',
        region: 'us-east-1',
        type: 'a',
      };

      const included: IServerGroup = {
        account: 'test',
        cloudProvider: 'aws',
        cluster: 'foo',
        instanceCounts: { up: 1, down: 0, succeeded: 0, failed: 0, unknown: 0, outOfService: 0, starting: 0 },
        instances: [{ name: 'b', id: 'b', launchTime: 1, zone: 'b', health: null }],
        name: 'foo-v002',
        region: 'us-east-1',
        type: 'a',
      };

      app.clusters = [
        {
          name: 'foo',
          account: 'test',
          cloudProvider: '',
          category: '',
          serverGroups: [serverGroup, omitted, included],
        },
      ];
      const params: IConfirmationModalParams = { account: 'prod' };
      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, params);
      expect(params.body).toBeDefined();
      expect(params.body.includes('foo-v000')).toBe(false); // this is the target, so should not be included
      expect(params.body.includes('foo-v001')).toBe(false);
      expect(params.body.includes('foo-v002')).toBe(true);
      expect(params.account).toBeUndefined();
    });
  });
});
