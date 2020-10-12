import React from 'react';
import { shallow, ShallowWrapper } from 'enzyme';

import LabelEditor, { ILabelEditorProps } from './LabelEditor';

describe('<LabelEditor />', () => {
  let onChangeSpy: jasmine.Spy;
  let props: ILabelEditorProps;
  let component: ShallowWrapper<LabelEditor>;

  beforeEach(() => {
    onChangeSpy = jasmine.createSpy('onChangeSpy');
    props = {
      labelSelectors: [
        {
          key: 'my-label-1',
          kind: 'EQUALS',
          values: ['my-value-1', 'my-value-2'],
        },
        {
          key: 'my-label-2',
          kind: 'NOT_EQUALS',
          values: ['my-value-3'],
        },
      ],
      onLabelSelectorsChange: onChangeSpy,
    };
    component = shallow(<LabelEditor {...props} />);
  });

  describe('view', () => {
    it('renders a row for each label selector', () => {
      expect(component.find('.label-editor-selector-row').length).toEqual(props.labelSelectors.length);
    });
    it('renders selector values as comma-separated lists', () => {
      expect(component.find('.label-editor-values-input').at(0).props().value).toEqual('my-value-1, my-value-2');
      expect(component.find('.label-editor-values-input').at(1).props().value).toEqual('my-value-3');
    });
  });

  describe('functionality', () => {
    it('calls `props.onLabelSelectorsChange` when selector properties are changed', () => {
      component
        .find('.label-editor-key-input')
        .at(0)
        .simulate('change', { target: { value: 'my-label-1-edited' } });
      expect(onChangeSpy).toHaveBeenCalledWith([
        {
          ...props.labelSelectors[0],
          key: 'my-label-1-edited',
        },
        props.labelSelectors[1],
      ]);
      component
        .find('.label-editor-values-input')
        .at(1)
        .simulate('change', { target: { value: 'my-value-3, my-value-4' } });
      expect(onChangeSpy).toHaveBeenCalledWith([
        props.labelSelectors[0],
        {
          ...props.labelSelectors[1],
          values: ['my-value-3', 'my-value-4'],
        },
      ]);
    });
    it('handles adding label selectors', () => {
      component.find('.add-new').simulate('click');
      expect(onChangeSpy).toHaveBeenCalledWith([...props.labelSelectors, { key: '', kind: 'EQUALS', values: [] }]);
    });
    it('handles removing label selectors', () => {
      component.find('.label-editor-remove').at(1).simulate('click');
      expect(onChangeSpy).toHaveBeenCalledWith([
        {
          ...props.labelSelectors[0],
        },
      ]);
    });
  });
});
