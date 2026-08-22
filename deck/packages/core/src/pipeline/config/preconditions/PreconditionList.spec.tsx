import { mount } from 'enzyme';
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
    onChange: jasmine.createSpy('onChange'),
    preconditions: [expressionPrecondition] as any[],
    strategy: false,
    upstreamStages: [{ name: 'Bake' }] as any[],
    ...overrides,
  });

  it('renders precondition type, context details, and fail pipeline value', () => {
    const component = mount(<PreconditionList {...createProps()} />);

    const rowText = component.find('tbody tr').at(0).text();

    expect(rowText).toContain('Expression');
    expect(rowText).toContain('Expression: ${foo}');
    expect(rowText).toContain('Failure Message: stop');
    expect(rowText).toContain('Fail Pipeline: true');
  });

  it('adds a precondition from the edit modal result', async () => {
    const props = createProps({ preconditions: [] });
    const newPrecondition = { type: 'expression', failPipeline: true, context: { expression: '${bar}' } };
    spyOn(EditPreconditionModal, 'show').and.returnValue(Promise.resolve(newPrecondition));
    const component = mount(<PreconditionList {...props} />);

    component.find('button.add-new').simulate('click');
    await Promise.resolve();

    expect(EditPreconditionModal.show).toHaveBeenCalledWith({
      application: props.application,
      precondition: undefined,
      strategy: props.strategy,
      upstreamStages: props.upstreamStages,
    });
    expect(props.onChange).toHaveBeenCalledWith([newPrecondition]);
  });

  it('edits a precondition from the edit modal result', async () => {
    const props = createProps();
    const updatedPrecondition = { type: 'expression', failPipeline: false, context: { expression: '${updated}' } };
    spyOn(EditPreconditionModal, 'show').and.returnValue(Promise.resolve(updatedPrecondition));
    const component = mount(<PreconditionList {...props} />);

    component.find('button[data-action="edit"]').simulate('click');
    await Promise.resolve();

    expect(EditPreconditionModal.show).toHaveBeenCalledWith({
      application: props.application,
      precondition: expressionPrecondition,
      strategy: props.strategy,
      upstreamStages: props.upstreamStages,
    });
    expect(props.onChange).toHaveBeenCalledWith([updatedPrecondition]);
  });

  it('removes a precondition', () => {
    const props = createProps();
    const component = mount(<PreconditionList {...props} />);

    component.find('button[data-action="remove"]').simulate('click');

    expect(props.onChange).toHaveBeenCalledWith([]);
  });
});
