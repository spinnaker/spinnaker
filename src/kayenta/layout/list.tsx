import * as React from 'react';
import KayentaInput from './kayentaInput';
import { IKayentaAction } from '../actions/creators';
import AddNewButton from './addNewButton';
import DeleteButton from './deleteButton';

import './list.less';

export enum ListAction {
  Add,
  Edit,
  Delete,
}

export interface IUpdateListPayload {
  type: ListAction;
  index?: number;
  value?: string;
}

export const updateListReducer = (defaultValue = '') =>
  (state: string[], action: IKayentaAction<IUpdateListPayload>) => {
    const { index, value } = action.payload;
    switch (action.payload.type) {
      case ListAction.Add:
        return state.concat(defaultValue);
      case ListAction.Delete:
        return [...state.slice(0, index), ...state.slice(index + 1)];
      case ListAction.Edit:
        return [...state.slice(0, index), value, ...state.slice(index + 1)];
    }
  };

interface IListProps {
  list: string[];
  valueKey?: (value: string) => string;
  actionCreator: (action: IUpdateListPayload) => void;
}

export const List = ({ list, valueKey, actionCreator }: IListProps) => {
  const onChange = (i: number) =>
    (event: any) => actionCreator({
      type: ListAction.Edit,
      index: i,
      value: event.target.value,
    });

  const deleteValue = (i: number) =>
    () => actionCreator({
      type: ListAction.Delete,
      index: i,
    });

  const addValue = () => actionCreator({
    type: ListAction.Add,
  });

  return (
    <section>
      {
        list.map((value, i) =>
          <div key={valueKey ? valueKey(value) : i} className="horizontal form-group kayenta-list">
            <KayentaInput
              value={value}
              onChange={onChange(i)}
            />
            <DeleteButton onClick={deleteValue(i)}/>
          </div>
        )
      }
      <AddNewButton onClick={addValue}/>
    </section>
  );
};

