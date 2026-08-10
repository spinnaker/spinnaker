import { mount } from 'enzyme';
import React from 'react';

import { EditPreconditionModal } from './EditPreconditionModal';
import { PreconditionSelector } from './PreconditionSelector';

describe('<EditPreconditionModal />', () => {
  const createProps = (overrides = {}) => ({
    application: { getDataSource: () => ({ data: [] }) } as any,
    closeModal: jasmine.createSpy('closeModal'),
    dismissModal: jasmine.createSpy('dismissModal'),
    precondition: {
      type: 'expression',
      failPipeline: true,
      context: { expression: '${foo}' },
    },
    strategy: false,
    upstreamStages: [{ name: 'Bake' }] as any[],
    ...overrides,
  });

  it('edits a cloned precondition and submits the edited copy', () => {
    const props = createProps();
    const component = mount(<EditPreconditionModal {...props} />);
    const updatedPrecondition = { type: 'expression', failPipeline: false, context: { expression: '${bar}' } };

    const selector = component.find(PreconditionSelector);
    expect(selector.prop('precondition')).not.toBe(props.precondition);

    selector.prop('onChange')(updatedPrecondition);
    const submitButton = component.find('button[data-purpose="submit"]');

    expect(submitButton.prop('disabled')).toBe(false);
    submitButton.simulate('click');

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
    const component = mount(<EditPreconditionModal {...props} />);

    expect(component.find('button[data-purpose="submit"]').prop('disabled')).toBe(true);
  });

  it('disables submit when a cluster size precondition is missing required fields', () => {
    const props = createProps({
      precondition: {
        type: 'clusterSize',
        failPipeline: true,
        context: { comparison: '==' },
      },
    });
    const component = mount(<EditPreconditionModal {...props} />);

    expect(component.find('button[data-purpose="submit"]').prop('disabled')).toBe(true);
  });

  it('disables submit when a stage status precondition is missing stage status fields', () => {
    const props = createProps({
      precondition: {
        type: 'stageStatus',
        failPipeline: true,
        context: { stageName: 'Bake' },
      },
    });
    const component = mount(<EditPreconditionModal {...props} />);

    expect(component.find('button[data-purpose="submit"]').prop('disabled')).toBe(true);
  });
});
