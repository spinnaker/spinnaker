import type { UIRouterReact } from '@uirouter/react';
import { UIRouterContext, UIViewContext } from '@uirouter/react';
import { mock } from 'angular';
import { mount } from 'enzyme';
import React from 'react';

import { AccountService } from '../../account';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ProviderSelectionService } from '../../cloudProvider/providerSelection/ProviderSelectionService';
import { ConfirmationModalService } from '../../confirmationModal';
import { REACT_MODULE } from '../../reactShims';
import { ClusterState } from '../../state';
import { MultipleServerGroupsDetails } from './MultipleServerGroupsDetails';

describe('<MultipleServerGroupsDetails />', () => {
  let previousMultiselectModel: any;
  let serverGroupWriter: any;
  let $uiRouter: UIRouterReact;

  const app = {
    serverGroups: {
      data: [
        {
          account: 'prod',
          instanceCounts: { down: 1, total: 3, up: 2 },
          isDisabled: false,
          name: 'app-v001',
          provider: 'aws',
          region: 'us-west-2',
          type: 'aws',
        },
      ],
      onRefresh: jasmine.createSpy('onRefresh').and.returnValue(() => null),
    },
  } as any;

  const selectedServerGroup = {
    account: 'prod',
    name: 'app-v001',
    provider: 'aws',
    region: 'us-west-2',
    type: 'aws',
  } as any;

  const mountDetails = () =>
    mount(
      <UIRouterContext.Provider value={$uiRouter}>
        <UIViewContext.Provider
          value={{
            fqn: 'application.insight.multipleServerGroups',
            context: $uiRouter.stateRegistry.get('application.insight.multipleServerGroups') as any,
          }}
        >
          <DeckRuntimeContext.Provider
            value={
              {
                services: {
                  providerServiceDelegate: {
                    getDelegate: () => ({
                      destroyServerGroup: (serverGroup: any) => ({ mixinName: serverGroup.name }),
                    }),
                    hasDelegate: () => true,
                  },
                  serverGroupWriter,
                },
              } as any
            }
          >
            <MultipleServerGroupsDetails app={app} />
          </DeckRuntimeContext.Provider>
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(
    mock.inject((_$uiRouter_: UIRouterReact) => {
      $uiRouter = _$uiRouter_;
      ['application', 'application.insight', 'application.insight.multipleServerGroups'].forEach((name) => {
        if (!$uiRouter.stateRegistry.get(name)) {
          $uiRouter.stateRegistry.register({ name, url: `/${name.split('.').pop()}` } as any);
        }
      });
    }),
  );

  beforeEach(() => {
    previousMultiselectModel = ClusterState.multiselectModel;
    ClusterState.multiselectModel = {
      clearAllServerGroups: () => null,
      serverGroups: [selectedServerGroup],
      serverGroupsStream: { subscribe: () => null },
    } as any;

    serverGroupWriter = {
      destroyServerGroup: jasmine.createSpy('destroyServerGroup').and.returnValue(Promise.resolve({})),
    };

    spyOn(AccountService, 'challengeDestructiveActions').and.returnValue(Promise.resolve(false));
    spyOn(ProviderSelectionService, 'isDisabled').and.returnValue(Promise.resolve(false));
    spyOn(ConfirmationModalService, 'confirm').and.stub();
    spyOn(ClusterState.multiselectModel.serverGroupsStream, 'subscribe').and.callFake((callback: any) => {
      callback();
      return { unsubscribe: jasmine.createSpy('unsubscribe') } as any;
    });
    spyOn(ClusterState.multiselectModel, 'clearAllServerGroups').and.stub();
  });

  afterEach(() => {
    ClusterState.multiselectModel = previousMultiselectModel;
  });

  it('renders selected server group details', () => {
    const wrapper = mountDetails();

    expect(wrapper.find('.details-panel h3').text()).toContain('1 Server Group');
    expect(wrapper.text()).toContain('app-v001');
    expect(wrapper.text()).toContain('prod');
    expect(wrapper.text()).toContain('us-west-2');
    expect(wrapper.find('.instance-health-counts').text()).toContain('2');
    expect(wrapper.find('.instance-health-counts').text()).toContain('1');

    wrapper.unmount();
  });

  it('opens destroy confirmation using legacy task monitor semantics', () => {
    const wrapper = mountDetails();

    wrapper.find('button.dropdown-toggle').simulate('click');
    wrapper
      .find('a')
      .filterWhere((node) => node.text() === 'Destroy')
      .simulate('click');

    expect(ConfirmationModalService.confirm).toHaveBeenCalledWith(
      jasmine.objectContaining({
        askForReason: true,
        buttonText: 'Destroy 1 server group',
        textToVerify: '1',
      }),
    );
    const confirmation = (ConfirmationModalService.confirm as jasmine.Spy).calls.mostRecent().args[0];

    confirmation.taskMonitorConfigs[0].submitMethod({ reason: 'user reason' });

    expect(serverGroupWriter.destroyServerGroup).toHaveBeenCalledWith(
      jasmine.objectContaining({
        account: 'prod',
        name: 'app-v001',
        region: 'us-west-2',
      }),
      app,
      jasmine.objectContaining({
        mixinName: 'app-v001',
        reason: 'user reason',
      }),
    );

    wrapper.unmount();
  });

  it('clears server group multiselect on unmount only when more than one group is selected', () => {
    const wrapper = mountDetails();

    wrapper.unmount();

    expect(ClusterState.multiselectModel.clearAllServerGroups).not.toHaveBeenCalled();

    ClusterState.multiselectModel.serverGroups = [
      selectedServerGroup,
      { ...selectedServerGroup, name: 'app-v002' },
    ] as any;
    const multiWrapper = mountDetails();

    multiWrapper.unmount();

    expect(ClusterState.multiselectModel.clearAllServerGroups).toHaveBeenCalled();
  });
});
