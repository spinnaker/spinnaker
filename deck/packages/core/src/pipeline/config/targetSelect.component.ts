import type { IComponentOptions } from 'angular';
import { module } from 'angular';
import React from 'react';

import { angularComponentFromReact } from '../../angular/angularComponentFromReact';
import type { IStageConstant } from './stages/stageConstants';

export interface ITargetSelectProps {
  options: IStageConstant[];
  model: { target: string };
  onChange(target: string): void;
}

export function TargetSelect(props: ITargetSelectProps) {
  const [isOpen, setIsOpen] = React.useState(false);
  const [search, setSearch] = React.useState('');
  const [selectedTarget, setSelectedTarget] = React.useState(props.model.target);
  const selectedOption = props.options.find((option) => option.val === selectedTarget);
  const normalizedSearch = search.toLowerCase();
  const filteredOptions = props.options.filter((option) => {
    return [option.label, option.description].some((value) => (value || '').toLowerCase().includes(normalizedSearch));
  });

  const onChange = (target: string) => {
    props.model.target = target;
    setSelectedTarget(target);
    props.onChange && props.onChange(target);
    setSearch('');
    setIsOpen(false);
  };

  return React.createElement(
    'div',
    {
      className: 'target-select',
    },
    React.createElement('input', {
      'aria-autocomplete': 'list',
      'aria-expanded': isOpen,
      className: 'form-control input-sm target-select-search',
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => {
        setSearch(event.target.value);
        setIsOpen(true);
      },
      onFocus: () => setIsOpen(true),
      placeholder: selectedOption ? selectedOption.label : 'None',
      role: 'combobox',
      type: 'text',
      value: search,
    }),
    props.model.target
      ? React.createElement(
          'button',
          {
            className: 'btn btn-link btn-sm target-select-clear',
            onClick: () => onChange(''),
            type: 'button',
          },
          'None',
        )
      : null,
    isOpen
      ? React.createElement(
          'div',
          { className: 'target-select-options', role: 'listbox' },
          filteredOptions.map((option) =>
            React.createElement(
              'button',
              {
                className: 'target-select-option',
                key: option.val,
                onClick: () => onChange(option.val),
                type: 'button',
              },
              React.createElement('strong', null, option.label),
              React.createElement('div', { className: 'target-select-description' }, option.description),
            ),
          ),
        )
      : null,
  );
}

export const targetSelectComponent: IComponentOptions = angularComponentFromReact(TargetSelect, 'targetSelect', [
  'options',
  'model',
  'onChange',
]);

export const TARGET_SELECT_COMPONENT = 'spinnaker.pipeline.targetSelect.component';
module(TARGET_SELECT_COMPONENT, []).component('targetSelect', targetSelectComponent);
