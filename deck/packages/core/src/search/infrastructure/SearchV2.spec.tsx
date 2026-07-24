import { shallow } from 'enzyme';
import React from 'react';
import { BehaviorSubject } from 'rxjs';

import { SearchV2Component } from './SearchV2';

describe('SearchV2', () => {
  const deckRuntimeServices = {
    pageTitleService: { handleRoutingSuccess: jasmine.createSpy('handleRoutingSuccess') },
  } as any;

  it('uses injected route state for tab selection and filter navigation', () => {
    const go = jasmine.createSpy('go');
    const component = shallow(
      <SearchV2Component
        deckRuntimeServices={deckRuntimeServices}
        router={{ globals: { params$: new BehaviorSubject({ tab: 'applications' }) } } as any}
        stateParams={{ tab: 'applications' }}
        stateService={{ go } as any}
      />,
      { disableLifecycleMethods: true },
    );

    expect(component.state('selectedTab')).toBe('applications');

    (component.instance() as SearchV2Component).handleFilterChange([{ key: 'name', text: 'payments' } as any]);

    expect(go).toHaveBeenCalledWith(
      '.',
      { account: undefined, key: undefined, name: 'payments', region: undefined, stack: undefined },
      { location: 'replace' },
    );
  });
});
