import { mount } from 'enzyme';
import React from 'react';

import { ChangesModal } from './ChangesModal';
import { ModalContext } from '../presentation/modal/ModalContext';

const mountModal = (modal: React.ReactElement) =>
  mount(<ModalContext.Provider value={{ onRequestClose: () => {} }}>{modal}</ModalContext.Provider>);

describe('ChangesModal', () => {
  it('renders safely when optional change data is absent', () => {
    const wrapper = mountModal(<ChangesModal dismissModal={() => {}} nameItem={{ name: 'Deploy' }} />);

    expect(wrapper.text()).toContain('Changes to Deploy');
    expect(wrapper.text()).not.toContain('Commits');
    expect(wrapper.text()).not.toContain('JAR Changes');
    wrapper.unmount();
  });

  it('renders build numbers without links when Jenkins metadata is absent', () => {
    const wrapper = mountModal(
      <ChangesModal
        buildInfo={{ ancestor: '100', target: '101' }}
        dismissModal={() => {}}
        nameItem={{ name: 'Deploy' }}
      />,
    );

    expect(wrapper.text()).toContain('Previous: Build: #100');
    expect(wrapper.text()).toContain('Current: Build: #101');
    expect(wrapper.find('a').length).toBe(0);
    wrapper.unmount();
  });
});
