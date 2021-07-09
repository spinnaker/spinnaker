import { mock } from 'angular';
import { GCE_SECURITY_GROUP_HELP_TEXT_SERVICE, GceSecurityGroupHelpTextService } from './securityGroupHelpText.service';

describe('Service: gceSecurityGroupHelpTextService', () => {
  let $q: ng.IQService;
  let gceSecurityGroupHelpTextService: GceSecurityGroupHelpTextService;
  let application: any;

  beforeEach(mock.module(GCE_SECURITY_GROUP_HELP_TEXT_SERVICE));

  beforeEach(
    mock.inject(
      (
        _gceSecurityGroupHelpTextService_: GceSecurityGroupHelpTextService,
        _$q_: ng.IQService,
        _$timeout_: ng.ITimeoutService,
      ) => {
        gceSecurityGroupHelpTextService = _gceSecurityGroupHelpTextService_;
        $q = _$q_;
        const $timeout = _$timeout_;

        application = {
          ready: () => $q.resolve(),
          getDataSource: () => {
            return {
              data: [
                {
                  name: 'match-v000',
                  account: 'my-google-account',
                  providerMetadata: {
                    networkName: 'default',
                    tags: ['tag-a', 'tag-b'],
                  },
                },
                {
                  name: 'match-v001',
                  account: 'my-google-account',
                  providerMetadata: {
                    networkName: 'default',
                    tags: ['tag-a'],
                  },
                },
                {
                  name: 'other-account-v000',
                  account: 'my-other-google-account',
                  providerMetadata: {
                    networkName: 'default',
                    tags: ['tag-a', 'tag-b'],
                  },
                },
                {
                  name: 'other-network-v000',
                  account: 'my-google-account',
                  providerMetadata: {
                    networkName: 'other-network',
                    tags: ['tag-a', 'tag-b'],
                  },
                },
              ],
            };
          },
        };

        gceSecurityGroupHelpTextService.register(application, 'my-google-account', 'default');
        $timeout.flush();
      },
    ),
  );

  it("should list server groups that match the rule's account and network and have a matching tag", () => {
    let helpText = gceSecurityGroupHelpTextService.getHelpTextForTag('tag-a', 'source');
    expect(helpText).toContain('match-v000');
    expect(helpText).toContain('match-v001');
    expect(helpText).not.toContain('other-account-v000');
    expect(helpText).not.toContain('other-network-v000');

    helpText = gceSecurityGroupHelpTextService.getHelpTextForTag('tag-b', 'target');
    expect(helpText).toContain('match-v000');
    expect(helpText).not.toContain('match-v001');
    expect(helpText).not.toContain('other-account-v000');
    expect(helpText).not.toContain('other-network-v000');
  });

  it('should be ok if no server groups match the given tag', () => {
    const helpText = gceSecurityGroupHelpTextService.getHelpTextForTag('no-matches-tag', 'source');
    expect(() => gceSecurityGroupHelpTextService.getHelpTextForTag('no-matches-tag', 'source')).not.toThrow();
    expect(helpText).not.toContain('match-v000');
    expect(helpText).not.toContain('match-v001');
    expect(helpText).not.toContain('other-account-v000');
    expect(helpText).not.toContain('other-network-v000');
  });

  it('should have no matches after a reset', () => {
    gceSecurityGroupHelpTextService.reset();
    const helpText = gceSecurityGroupHelpTextService.getHelpTextForTag('tag-a', 'source');
    expect(helpText).not.toContain('match-v000');
    expect(helpText).not.toContain('match-v001');
    expect(helpText).not.toContain('other-account-v000');
    expect(helpText).not.toContain('other-network-v000');
  });
});
