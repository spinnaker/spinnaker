import React from 'react';
import { shallow, ShallowWrapper } from 'enzyme';

import { StageConfigField } from '@spinnaker/core';

import DeleteManifestOptionsForm, { IDeleteManifestOptionsFormProps } from './DeleteManifestOptionsForm';

describe('<DeleteManifestOptionsForm />', () => {
  let onChangeSpy: jasmine.Spy;
  let props: IDeleteManifestOptionsFormProps;
  let component: ShallowWrapper<IDeleteManifestOptionsFormProps>;

  beforeEach(() => {
    onChangeSpy = jasmine.createSpy('onChangeSpy');
    props = {
      onOptionsChange: onChangeSpy,
      options: {
        cascading: true,
        gracePeriodSeconds: 60,
      },
    };
    component = shallow(<DeleteManifestOptionsForm {...props} />);
  });

  describe('view', () => {
    it('renders a StageConfigField for Cascading and Grace Period options', () => {
      expect(component.find(StageConfigField).length).toEqual(2);
      expect(component.find(StageConfigField).at(0).prop('label')).toEqual('Cascading');
      expect(component.find(StageConfigField).at(1).prop('label')).toEqual('Grace Period');
    });
  });
  describe('functionality', () => {
    it('calls `props.onOptionsChange` when cascading is toggled', () => {
      component
        .find(StageConfigField)
        .at(0)
        .find('input')
        .simulate('change', { target: { checked: false } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: false,
        gracePeriodSeconds: 60,
      });
      component
        .find(StageConfigField)
        .at(0)
        .find('input')
        .simulate('change', { target: { checked: true } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: 60,
      });
    });
    it('calls `props.onOptionsChange` when grace period is changed', () => {
      component
        .find(StageConfigField)
        .at(1)
        .find('input')
        .simulate('change', { target: { value: 0 } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: 0,
      });
      component
        .find(StageConfigField)
        .at(1)
        .find('input')
        .simulate('change', { target: { value: 100 } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: 100,
      });
      component
        .find(StageConfigField)
        .at(1)
        .find('input')
        .simulate('change', { target: { value: '' } });
      expect(onChangeSpy).toHaveBeenCalledWith({
        cascading: true,
        gracePeriodSeconds: null,
      });
    });
  });
});
