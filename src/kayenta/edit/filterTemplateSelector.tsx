import * as React from 'react';
import { Option } from 'react-select';

import FormRow from 'kayenta/layout/formRow';
import { DisableableReactSelect, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';

export interface IFilterTemplateSelectorProps {
  templates: { [name: string]: string };
  template: string;
  select: (template: Option) => void;
}

export function FilterTemplateSelector({ template, templates, select }: IFilterTemplateSelectorProps) {
  const options = Object.keys(templates).map(t => ({ value: t, label: t }));

  return (
    <>
      <FormRow label="Filter Template">
        <DisableableReactSelect
          value={template}
          options={options}
          onChange={select}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <FormRow>{template && templates && templates[template] && <pre>{templates[template]}</pre>}</FormRow>
    </>
  );
}
