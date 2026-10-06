import { fireEvent, render, screen } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
import React from 'react';

import { EditPreconditionModal } from './EditPreconditionModal';
import { AccountService } from '../../../account/AccountService';

describe('<EditPreconditionModal />', () => {
  // PreconditionSelector fetches accounts on mount; isolate the unit from the network (as the
  // sibling PreconditionSelector spec does) so the fail-closed HTTP client sees no request.
  beforeEach(() => {
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([] as any);
  });

  const createProps = (overrides = {}) => ({
    application: { getDataSource: () => ({ data: [] }) } as any,
    closeModal: vi.fn(),
    dismissModal: vi.fn(),
    precondition: {
      type: 'expression',
      failPipeline: true,
      context: { expression: '${foo}' },
    },
    strategy: false,
    upstreamStages: [{ name: 'Bake' }] as any[],
    ...overrides,
  });

  it('edits a cloned precondition and submits the edited copy', async () => {
    const user = setupUser();
    const props = createProps();
    const updatedPrecondition = { type: 'expression', failPipeline: false, context: { expression: '${bar}' } };
    render(<EditPreconditionModal {...props} />);

    const expression = screen.getByRole('textbox', { name: 'Expression' });
    fireEvent.change(expression, { target: { value: updatedPrecondition.context.expression } });
    await user.click(screen.getByRole('checkbox', { name: 'Fail Pipeline' }));
    const submitButton = screen.getByRole('button', { name: /Update/ });
    expect(submitButton).toBeEnabled();
    await user.click(submitButton);

    expect(props.precondition.context.expression).toBe('${foo}');
    expect(props.closeModal).toHaveBeenCalledWith(updatedPrecondition);
  });

  it('disables submit when an expression precondition is missing the expression', () => {
    const props = createProps({
      precondition: {
        type: 'expression',
        failPipeline: true,
        context: {},
      },
    });
    render(<EditPreconditionModal {...props} />);

    expect(screen.getByRole('button', { name: /Update/ })).toBeDisabled();
  });

  it('disables submit when a cluster size precondition is missing required fields', () => {
    const props = createProps({
      precondition: {
        type: 'clusterSize',
        failPipeline: true,
        context: { comparison: '==' },
      },
    });
    render(<EditPreconditionModal {...props} />);

    expect(screen.getByRole('button', { name: /Update/ })).toBeDisabled();
  });

  it('disables submit when a stage status precondition is missing stage status fields', () => {
    const props = createProps({
      precondition: {
        type: 'stageStatus',
        failPipeline: true,
        context: { stageName: 'Bake' },
      },
    });
    render(<EditPreconditionModal {...props} />);

    expect(screen.getByRole('button', { name: /Update/ })).toBeDisabled();
  });
});
