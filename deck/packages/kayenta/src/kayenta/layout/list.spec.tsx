import { fireEvent, render, screen } from '@testing-library/react';
import { IKayentaAction } from '../actions/creators';
import * as React from 'react';
import { Provider } from 'react-redux';
import { createStore } from 'redux';

import { IUpdateListPayload, List, ListAction, updateListReducer } from './list';
import { rootReducer } from '../reducers';

describe('Reducer: updateListReducer', () => {
  const createAction = (payload: IUpdateListPayload): IKayentaAction<IUpdateListPayload> => ({
    type: 'update_list',
    payload,
  });

  const reducer = updateListReducer('defaultValue');

  it('adds', () => {
    expect(
      reducer(
        [],
        createAction({
          type: ListAction.Add,
        }),
      ),
    ).toEqual(['defaultValue']);
  });

  it('deletes', () => {
    expect(
      reducer(
        ['toRemain', 'toRemove'],
        createAction({
          type: ListAction.Delete,
          index: 1,
        }),
      ),
    ).toEqual(['toRemain']);
  });

  it('edits', () => {
    expect(
      reducer(
        ['unedited', 'beforeEdit'],
        createAction({
          type: ListAction.Edit,
          index: 1,
          value: 'afterEdit',
        }),
      ),
    ).toEqual(['unedited', 'afterEdit']);
  });
});

describe('Component: List', () => {
  it('renders a list', () => {
    render(
      <Provider store={createStore(rootReducer)}>
        <List list={['a', 'b', 'c']} actionCreator={() => null} />
      </Provider>,
    );

    expect(screen.getAllByRole('textbox')).toHaveLength(3);
  });

  it('emits the correct value and index on update', () => {
    const spy = vi.fn();
    render(
      <Provider store={createStore(rootReducer)}>
        <List list={['a', 'b', 'c']} actionCreator={spy} />
      </Provider>,
    );

    fireEvent.change(screen.getAllByRole('textbox')[1], { target: { value: 'newValue' } });

    expect(spy).toHaveBeenCalledWith({
      type: ListAction.Edit,
      index: 1,
      value: 'newValue',
    });
  });
});
