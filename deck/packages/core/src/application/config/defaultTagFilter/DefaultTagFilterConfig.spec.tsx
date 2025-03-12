import { shallow } from 'enzyme';
import React from 'react';

import type { IDefaultTagFilterConfig } from './DefaultTagFilterConfig';
import { DefaultTagFilterConfig } from './DefaultTagFilterConfig';
import { noop } from '../../../utils';

describe('<DefaultTagFilterConfig />', () => {
  let tagConfigs: IDefaultTagFilterConfig[];
  let wrapper: any;

  beforeEach(() => {
    tagConfigs = getTestDefaultFilterTagConfigs();
    wrapper = shallow(
      <DefaultTagFilterConfig
        defaultTagFilterConfigs={tagConfigs}
        isSaving={false}
        saveError={false}
        updateDefaultTagFilterConfigs={noop}
      />,
    );
  });

  describe('view', () => {
    it('renders a row for each banner config', () => {
      expect(wrapper.find('.default-filter-config-row').length).toEqual(tagConfigs.length);
    });
    it('renders an "add" button', () => {
      expect(wrapper.find('.add-new').length).toEqual(1);
    });
  });

  describe('functionality', () => {
    it('update default tag filter config', () => {
      expect(wrapper.state('defaultTagFilterConfigsEditing')).toEqual(tagConfigs);
      wrapper
        .find('textarea')
        .at(1)
        .simulate('change', { target: { value: 'hello' } });
      const updatedConfigs = [
        {
          ...tagConfigs[0],
          tagValue: 'hello',
        },
        {
          ...tagConfigs[1],
        },
      ];
      expect(wrapper.state('defaultTagFilterConfigsEditing')).toEqual(updatedConfigs);
    });
    it('add default filter tag config', () => {
      expect(wrapper.state('defaultTagFilterConfigsEditing').length).toEqual(2);
      wrapper.find('.add-new').simulate('click');
      expect(wrapper.state('defaultTagFilterConfigsEditing').length).toEqual(3);
    });
    it('remove default filter tag config', () => {
      expect(wrapper.state('defaultTagFilterConfigsEditing').length).toEqual(2);
      wrapper.find('.default-filter-config-remove').at(1).simulate('click');
      expect(wrapper.state('defaultTagFilterConfigsEditing').length).toEqual(1);
    });
  });
});

export function getTestDefaultFilterTagConfigs(): IDefaultTagFilterConfig[] {
  return [
    {
      tagName: 'Pipeline Type',
      tagValue: 'Deployment Pipelines',
    },
    {
      tagName: 'Pipeline Type',
      tagValue: 'Repair Pipelines',
    },
  ];
}
