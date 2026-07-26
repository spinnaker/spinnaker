import { AccountService, ClusterTargetBuilder, ServerGroupReader } from '@spinnaker/core';

import { titusServerGroupDetailsGetter } from './titusServerGroupDetailsGetter';

describe('titusServerGroupDetailsGetter', () => {
  const app = {
    name: 'app',
    serverGroups: {
      data: [{ account: 'titus-account', name: 'app-main-v001', region: 'us-east-1' }],
    },
  } as any;
  const serverGroup = { accountId: 'titus-account', name: 'app-main-v001', region: 'us-east-1' } as any;
  const serverGroupDetails = {
    labels: {},
    name: 'app-main-v001',
    region: 'us-east-1',
    scalingPolicies: [],
  };

  beforeEach(() => {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(Promise.resolve({ ...serverGroupDetails }));
    spyOn(ClusterTargetBuilder, 'buildClusterTargets').and.returnValue([]);
  });

  it('emits Titus details when the account has no backing AWS account', (done) => {
    const getAccountDetails = spyOn(AccountService, 'getAccountDetails').and.callFake((account: string) => {
      if (account === 'titus-account') {
        return Promise.resolve({ regions: [{ name: 'us-east-1', endpoint: 'https://titus.example.test' }] });
      }
      return Promise.reject(new Error(`Unexpected account lookup: ${account}`));
    });
    const autoClose = jasmine.createSpy('autoClose').and.callFake(() => done.fail('details panel should stay open'));

    titusServerGroupDetailsGetter({ app, serverGroup } as any, autoClose).subscribe((details) => {
      expect(details.apiEndpoint).toBe('https://titus.example.test');
      expect(details.awsAccountId).toBeUndefined();
      expect(getAccountDetails).toHaveBeenCalledTimes(1);
      expect(autoClose).not.toHaveBeenCalled();
      done();
    }, done.fail);
  });

  it('emits base Titus details when the Titus account lookup fails', (done) => {
    const getAccountDetails = spyOn(AccountService, 'getAccountDetails').and.returnValue(
      Promise.reject(new Error('account lookup failed')),
    );
    const autoClose = jasmine.createSpy('autoClose').and.callFake(() => done.fail('details panel should stay open'));

    titusServerGroupDetailsGetter({ app, serverGroup } as any, autoClose).subscribe((details) => {
      expect(details.name).toBe('app-main-v001');
      expect(details.apiEndpoint).toBeUndefined();
      expect(details.awsAccountId).toBeUndefined();
      expect(getAccountDetails).toHaveBeenCalledOnceWith('titus-account');
      expect(autoClose).not.toHaveBeenCalled();
      done();
    }, done.fail);
  });

  it('emits Titus details when the backing AWS account lookup fails', (done) => {
    const getAccountDetails = spyOn(AccountService, 'getAccountDetails').and.callFake((account: string) => {
      if (account === 'titus-account') {
        return Promise.resolve({
          awsAccount: 'aws-prod',
          regions: [{ name: 'us-east-1', endpoint: 'https://titus.example.test' }],
        });
      }
      return Promise.reject(new Error(`Unexpected account lookup: ${account}`));
    });
    const autoClose = jasmine.createSpy('autoClose').and.callFake(() => done.fail('details panel should stay open'));

    titusServerGroupDetailsGetter({ app, serverGroup } as any, autoClose).subscribe((details) => {
      expect(details.apiEndpoint).toBe('https://titus.example.test');
      expect(details.awsAccountId).toBeUndefined();
      expect(getAccountDetails).toHaveBeenCalledTimes(2);
      expect(getAccountDetails).toHaveBeenCalledWith('aws-prod');
      expect(autoClose).not.toHaveBeenCalled();
      done();
    }, done.fail);
  });

  it('terminates the details observable when the server group lookup fails', (done) => {
    const error = new Error('server group lookup failed');
    (ServerGroupReader.getServerGroup as jasmine.Spy).and.returnValue(Promise.reject(error));
    const autoClose = jasmine.createSpy('autoClose');
    const timeout = setTimeout(() => done.fail('details observable did not error'), 20);

    titusServerGroupDetailsGetter({ app, serverGroup } as any, autoClose).subscribe(
      () => done.fail('details should not emit after lookup failure'),
      (err) => {
        clearTimeout(timeout);
        expect(err).toBe(error);
        expect(autoClose).toHaveBeenCalled();
        done();
      },
    );
  });
});
