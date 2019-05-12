import * as classNames from 'classnames';
import * as React from 'react';
import { IKayentaAction } from '../actions/creators';
import AddNewButton from './addNewButton';
import DeleteButton from './deleteButton';

import './keyValueList.less';
import { DISABLE_EDIT_CONFIG, DisableableInput } from './disableable';

export enum KeyValueListAction {
  ADD,
  DELETE,
  KEY_EDIT,
  VALUE_EDIT,
}

export interface IKeyValuePair {
  key: string;
  value: string;
}

export interface IUpdateKeyValueListPayload {
  type: KeyValueListAction;
  index?: number;
  key?: string;
  value?: string;
}

export const updateListReducer = (defaultValue = { key: '', value: '' }) => (
  state: IKeyValuePair[],
  action: IKayentaAction<IUpdateKeyValueListPayload>,
) => {
  const { index, value, type } = action.payload;
  switch (type) {
    case KeyValueListAction.ADD:
      return state.concat(defaultValue);
    case KeyValueListAction.DELETE:
      return [...state.slice(0, index), ...state.slice(index + 1)];
    case KeyValueListAction.KEY_EDIT:
      return [...state.slice(0, index), { ...state[index], key: value }, ...state.slice(index + 1)];
    case KeyValueListAction.VALUE_EDIT:
      return [...state.slice(0, index), { ...state[index], value }, ...state.slice(index + 1)];
  }
};

export interface IKeyValueListProps {
  list: IKeyValuePair[];
  className?: string;
  actionCreator: (action: IUpdateKeyValueListPayload) => void;
}

export default function KeyValueList({ list, className, actionCreator }: IKeyValueListProps) {
  const onKeyChange = (i: number) => (event: any) =>
    actionCreator({
      type: KeyValueListAction.KEY_EDIT,
      index: i,
      value: event.target.value,
    });

  const onValueChange = (i: number) => (event: any) =>
    actionCreator({
      type: KeyValueListAction.VALUE_EDIT,
      index: i,
      value: event.target.value,
    });

  const deleteValue = (i: number) => () =>
    actionCreator({
      type: KeyValueListAction.DELETE,
      index: i,
    });

  const addValue = () =>
    actionCreator({
      type: KeyValueListAction.ADD,
    });

  return (
    <section className={classNames('kv-pair-list', className)}>
      {list.map((kvPair, index) => {
        const { key, value } = kvPair;
        return (
          <div key={index} className="kv-pair-item">
            <div className="kv-pair-item-section">
              <div className="kv-pair-item-section-label-wrapper">
                <div className="kv-pair-item-section-label">Key</div>
              </div>
              <div className="kv-pair-item-section-input">
                <DisableableInput value={key} onChange={onKeyChange(index)} disabledStateKeys={[DISABLE_EDIT_CONFIG]} />
              </div>
            </div>
            <div className="kv-pair-item-section">
              <div className="kv-pair-item-section-label-wrapper">
                <div className="kv-pair-item-section-label">Value</div>
              </div>
              <div className="kv-pair-item-section-input">
                <DisableableInput
                  value={value}
                  onChange={onValueChange(index)}
                  disabledStateKeys={[DISABLE_EDIT_CONFIG]}
                />
              </div>
            </div>
            <div className="kv-pair-item-section">
              <DeleteButton onClick={deleteValue(index)} />
            </div>
          </div>
        );
      })}
      <AddNewButton onClick={addValue} />
    </section>
  );
}
