import { render, screen, within } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
import React from 'react';

import { EditPreconditionModal } from './EditPreconditionModal';
import { PreconditionList } from './PreconditionList';

describe('<PreconditionList />', () => {
  const expressionPrecondition = {
    type: 'expression',
    failPipeline: true,
    context: { expression: '${foo}', failureMessage: 'stop' },
  };

  const createProps = (overrides = {}) => ({
    application: {} as any,
    onChange: vi.fn(),
    preconditions: [expressionPrecondition] as any[],
    strategy: false,
    upstreamStages: [{ name: 'Bake' }] as any[],
    ...overrides,
  });

  it('renders precondition type, context details, and fail pipeline value', () => {
    render(<PreconditionList {...createProps()} />);

    const row = screen.getAllByRole('row')[1];

    expect(row).toHaveTextContent('Expression');
    expect(row).toHaveTextContent('Expression: ${foo}');
    expect(row).toHaveTextContent('Failure Message: stop');
    expect(row).toHaveTextContent('Fail Pipeline: true');
  });

  it('adds a precondition from the edit modal result', async () => {
    const user = setupUser();
    const props = createProps({ preconditions: [] });
    const newPrecondition = { type: 'expression', failPipeline: true, context: { expression: '${bar}' } };
    vi.spyOn(EditPreconditionModal, 'show').mockReturnValue(Promise.resolve(newPrecondition));
    render(<PreconditionList {...props} />);

    await user.click(screen.getByRole('button', { name: /Add Precondition/ }));

    expect(EditPreconditionModal.show).toHaveBeenCalledWith({
      application: props.application,
      precondition: undefined,
      strategy: props.strategy,
      upstreamStages: props.upstreamStages,
    });
    expect(props.onChange).toHaveBeenCalledWith([newPrecondition]);
  });

  it('edits a precondition from the edit modal result', async () => {
    const user = setupUser();
    const props = createProps();
    const updatedPrecondition = { type: 'expression', failPipeline: false, context: { expression: '${updated}' } };
    vi.spyOn(EditPreconditionModal, 'show').mockReturnValue(Promise.resolve(updatedPrecondition));
    render(<PreconditionList {...props} />);

    await user.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: 'Edit precondition' }));

    expect(EditPreconditionModal.show).toHaveBeenCalledWith({
      application: props.application,
      precondition: expressionPrecondition,
      strategy: props.strategy,
      upstreamStages: props.upstreamStages,
    });
    expect(props.onChange).toHaveBeenCalledWith([updatedPrecondition]);
  });

  it('removes a precondition', async () => {
    const user = setupUser();
    const props = createProps();
    render(<PreconditionList {...props} />);

    await user.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: 'Remove precondition' }));

    expect(props.onChange).toHaveBeenCalledWith([]);
  });
});
