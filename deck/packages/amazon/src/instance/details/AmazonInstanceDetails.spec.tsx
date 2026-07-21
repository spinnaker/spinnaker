import { AngularServices } from '@spinnaker/core';

import { AmazonInstanceDetails } from './AmazonInstanceDetails';

describe('AmazonInstanceDetails', () => {
  let $state: any;

  beforeEach(() => {
    $state = { go: jasmine.createSpy('go') };
    spyOnProperty(AngularServices, '$state', 'get').and.returnValue($state);
  });

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
        ready: jasmine.createSpy('serverGroups.ready').and.returnValue(Promise.resolve()),
        onRefresh: jasmine.createSpy('onRefresh'),
      },
      loadBalancers: {
        ready: jasmine.createSpy('loadBalancers.ready').and.returnValue(Promise.resolve()),
      },
    } as any;
    spyOn(Promise, 'all').and.returnValue(rejectedReadiness as any);

    const component = new AmazonInstanceDetails({
      app,
      $stateParams: { account: 'test', instanceId: 'i-123', provider: 'aws', region: 'us-east-1' },
    } as any);

    component.componentDidMount();

    expect($state.go).toHaveBeenCalledWith('^', { allowModalToStayOpen: true }, { location: 'replace' });
    expect(app.serverGroups.onRefresh).not.toHaveBeenCalled();
  });
});
