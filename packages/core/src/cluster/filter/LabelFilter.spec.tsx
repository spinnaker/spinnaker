import React from 'react';
import { shallow } from 'enzyme';

import { noop } from '../../utils';

import LabelFilter, { ILabelFilterProps, LabelFilterSelect } from './LabelFilter';

describe('<LabelFilter />', () => {
  let props: ILabelFilterProps;
  beforeEach(() => {
    props = getLabelFilterProps();
  });
  describe('render', () => {
    it('renders a <LabelFilterSelect /> for each labelFilter', () => {
      const component = shallow(<LabelFilter {...props} />);
      expect(component.find(LabelFilterSelect).length).toBe(2);
    });
    it('renders a "+" button', () => {
      const component = shallow(<LabelFilter {...props} />);
      expect(component.find('button .glyphicon-plus-sign').length).toBe(1);
    });
  });
  describe('getKeyOptions', () => {
    it('returns available label key options for filter at given index', () => {
      const component: any = shallow(<LabelFilter {...props} />);
      const keyOptionsIdx1 = [
        { label: 'key2', value: 'key2' },
        { label: 'key3', value: 'key3' },
      ];
      expect(component.instance().getKeyOptions(1)).toEqual(keyOptionsIdx1);
    });
  });
  describe('getValueOptions', () => {
    it('returns available label value options for given label key', () => {
      const key1ValueOptions = [
        { label: 'value1', value: 'value1' },
        { label: 'value2', value: 'value2' },
        { label: 'value3', value: 'value3' },
      ];
      const key2ValueOptions = [{ label: 'value4', value: 'value4' }];
      const component: any = shallow(<LabelFilter {...props} />);
      expect(component.instance().getValueOptions('key1')).toEqual(key1ValueOptions);
      expect(component.instance().getValueOptions('key2')).toEqual(key2ValueOptions);
    });
  });
});

function getLabelFilterProps(): ILabelFilterProps {
  return {
    labelsMap: {
      key1: ['value1', 'value2', 'value3'],
      key2: ['value4'],
      key3: ['value5', 'value6'],
    },
    labelFilters: [
      { key: 'key1', value: 'value1' },
      { key: 'key2', value: 'value2' },
    ],
    updateLabelFilters: noop,
  };
}
