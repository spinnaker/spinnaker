import * as React from 'react';
import { NumberInput, TextInput, Tooltip } from '../../presentation';

export interface INumberListConstraints {
  min: number;
  max: number;
}

export interface INumberListProps {
  constraints?: INumberListConstraints;
  label: string;
  model: number[] | string;
  onChange: (model: number[] | string) => void;
}

interface INumberListToggleProps {
  isNumber: boolean;
  toggle: (mode: boolean) => void;
}

const NumberListToggle = ({ isNumber, toggle }: INumberListToggleProps) => (
  <div className="flex-container-h btn-group btn-group-xs sp-padding-s-yaxis sp-margin-s-left">
    <button type="button" className={`btn btn-default ${isNumber ? 'active' : ''}`} onClick={() => toggle(true)}>
      <Tooltip value="Toggle to enter number">
        <span>Num</span>
      </Tooltip>
    </button>
    <button type="button" className={`btn btn-default ${isNumber ? '' : 'active'}`} onClick={() => toggle(false)}>
      <Tooltip value="Toggle to enter expression">
        <span>
          {'${'}&hellip;{'}'}
        </span>
      </Tooltip>
    </button>
  </div>
);

export const NumberList = ({ constraints, label, model, onChange }: INumberListProps) => {
  const [isNumList, setIsNumList] = React.useState(typeof model !== 'string');
  const [numListModel, setNumListModel] = React.useState(isNumList ? (model as number[]) : []);
  const spelModel = isNumList ? '' : (model as string);

  const updateModel = (list: number[]) => {
    setNumListModel(list);

    const sortedList = list.filter((n) => n !== null).sort((a, b) => a - b);
    onChange(sortedList);
  };

  const addNumber = () => {
    const newNumList = [...numListModel, null];
    updateModel(newNumList);
  };
  const removeNumber = (index: number) => {
    const newNumListModel = numListModel.filter((_, i) => i !== index);
    updateModel(newNumListModel);
  };

  const updateNumber = (num: string, index: number) => {
    const newNumList = [...numListModel];
    newNumList[index] = Number.parseInt(num, 10);

    updateModel(newNumList);
  };

  const toggleMode = (isNum: boolean) => {
    const newModel = isNum ? ([] as number[]) : '${}';
    setIsNumList(isNum);
    onChange(newModel);
  };

  return (
    <div>
      {!isNumList && (
        <div className="flex-container-h">
          <TextInput
            className="form-control"
            type="text"
            value={spelModel}
            onChange={(e) => onChange(e.target.value)}
          />
          <NumberListToggle isNumber={isNumList} toggle={toggleMode} />
        </div>
      )}
      {isNumList && (
        <div>
          {(numListModel || []).map((num, i) => (
            <div key={`number-list-${i}`} className="flex-container-h sp-padding-xs-yaxis">
              <NumberInput
                value={num}
                max={constraints?.max}
                min={constraints?.min}
                onChange={(e) => updateNumber(e.target.value, i)}
              />
              {i === 0 && <NumberListToggle isNumber={isNumList} toggle={toggleMode} />}
              {i > 0 && (
                <button
                  type="button"
                  className="btn btn-link btn-sm sp-margin-l-right sp-padding-xl-xaxis"
                  onClick={() => removeNumber(i)}
                >
                  <i className="glyphicon glyphicon-trash" />
                </button>
              )}
            </div>
          ))}
          <button className="btn btn-xs btn-block add-new" type="button" onClick={addNumber}>
            <i className="glyphicon glyphicon-plus-sign" />
            <span>{`Add ${label || 'Item'}`}</span>
          </button>
        </div>
      )}
    </div>
  );
};
