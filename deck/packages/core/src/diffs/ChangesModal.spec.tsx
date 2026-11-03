import { render, screen } from '@testing-library/react';
import React from 'react';

import { ChangesModal } from './ChangesModal';
import { ModalContext } from '../presentation/modal/ModalContext';

const renderModal = (modal: React.ReactElement) =>
  render(<ModalContext.Provider value={{ onRequestClose: () => {} }}>{modal}</ModalContext.Provider>);

describe('ChangesModal', () => {
  it('renders safely when optional change data is absent', () => {
    renderModal(<ChangesModal dismissModal={() => {}} nameItem={{ name: 'Deploy' }} />);

    expect(screen.getByText('Changes to Deploy')).toBeInTheDocument();
    expect(screen.queryByText('Commits')).not.toBeInTheDocument();
    expect(screen.queryByText('JAR Changes')).not.toBeInTheDocument();
  });

  it('renders build numbers without links when Jenkins metadata is absent', () => {
    renderModal(
      <ChangesModal
        buildInfo={{ ancestor: '100', target: '101' }}
        dismissModal={() => {}}
        nameItem={{ name: 'Deploy' }}
      />,
    );

    expect(screen.getByText('Previous:').parentElement).toHaveTextContent(/^Previous: Build: #100$/);
    expect(screen.getByText('Current:').parentElement).toHaveTextContent(/^Current: Build: #101$/);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
