import { ConfirmationModalService } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';

import {
  AmazonInstanceActionsComponent,
  AmazonInstanceDetailsComponent as AmazonInstanceDetails,
} from './AmazonInstanceDetails';

describe('AmazonInstanceDetails', () => {
  let adHocInfraWritesEnabled: boolean;
  let stateService: any;

  beforeEach(() => {
    adHocInfraWritesEnabled = AWSProviderSettings.adHocInfraWritesEnabled;
    AWSProviderSettings.adHocInfraWritesEnabled = true;
    stateService = { go: vi.fn(), includes: vi.fn().mockReturnValue(true) };
  });

  afterEach(() => (AWSProviderSettings.adHocInfraWritesEnabled = adHocInfraWritesEnabled));

  it('closes details when required data sources fail before instance loading', () => {
    const readyFailure = new Error('server groups failed');
    const rejectedReadiness = {
      then: (_onFulfilled: () => void, onRejected?: (error: Error) => void) => {
        if (onRejected) {
          onRejected(readyFailure);
        }
        return rejectedReadiness;
      },
      catch: (onRejected: (error: Error) => void) => {
        onRejected(readyFailure);
        return rejectedReadiness;
      },
    };
    const app = {
      isStandalone: false,
      serverGroups: {
        ready: vi.fn().mockReturnValue(Promise.resolve()),
        onRefresh: vi.fn(),
      },
      loadBalancers: {
        ready: vi.fn().mockReturnValue(Promise.resolve()),
      },
    } as any;
    vi.spyOn(Promise, 'all').mockReturnValue(rejectedReadiness as any);

    const component = new AmazonInstanceDetails({
      app,
      $stateParams: { account: 'test', instanceId: 'i-123', provider: 'aws', region: 'us-east-1' },
      router: {},
      stateParams: {},
      stateService,
    } as any);

    component.componentDidMount();

    expect(stateService.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' });
    expect(app.serverGroups.onRefresh).not.toHaveBeenCalled();
  });

  it('closes terminated instance details through the injected state service', () => {
    const confirmation = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    const instance = { account: 'test', health: [], instanceId: 'i-123', placement: {} } as any;
    const actions = AmazonInstanceActionsComponent({
      app: { attributes: {} } as any,
      instance,
      router: {} as any,
      stateParams: {},
      stateService,
    }).props.actions;

    actions.find(({ label }: any) => label === 'Terminate').triggerAction();
    confirmation.mock.lastCall[0].taskMonitorConfig.onTaskComplete();

    expect(stateService.includes).toHaveBeenCalledWith('**.instanceDetails', { instanceId: 'i-123' });
    expect(stateService.go).toHaveBeenCalledWith('^');
  });
});
