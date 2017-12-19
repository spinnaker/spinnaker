import * as React from 'react';
import { mount } from 'enzyme';
import { IUpdateListPayload, List, ListAction, updateListReducer } from './list';
import { IKayentaAction } from '../actions/creators';
import createSpy = jasmine.createSpy;

describe('Reducer: updateListReducer', () => {

  const createAction = (payload: IUpdateListPayload): IKayentaAction<IUpdateListPayload> => ({
    type: 'update_list',
    payload,
  });

  const reducer = updateListReducer('defaultValue');

  it('adds', () => {
    expect(reducer([], createAction({
      type: ListAction.Add,
    }))).toEqual(['defaultValue']);
  });

  it('deletes', () => {
    expect(reducer(['toRemain', 'toRemove'], createAction({
      type: ListAction.Delete,
      index: 1,
    }))).toEqual(['toRemain']);
  });

  it('edits', () => {
    expect(reducer(['unedited', 'beforeEdit'], createAction({
      type: ListAction.Edit,
      index: 1,
      value: 'afterEdit',
    }))).toEqual(['unedited', 'afterEdit']);
  });
});

describe('Component: List', () => {

  it('renders a list', () => {
    const component = mount(
      <List
        list={['a', 'b', 'c']}
        actionCreator={() => null}
      />
    );

    expect(component.find('input').length).toEqual(3);
  });

  it('emits the correct value and index on update', () => {
    const spy = createSpy('actionCreator');
    const component = mount(
      <List
        list={['a', 'b', 'c']}
        actionCreator={spy}
      />
    );

    const input = component.find('input').at(1);
    input.simulate('change', { target: { value: 'newValue' } });

    expect(spy).toHaveBeenCalledWith({
      type: ListAction.Edit,
      index: 1,
      value: 'newValue',
    });
  });
});
