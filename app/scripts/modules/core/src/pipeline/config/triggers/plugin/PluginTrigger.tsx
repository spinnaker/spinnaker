import React from 'react';
import { FormikFormField, SelectInput, StandardFieldLayout } from 'core/presentation';
import { FormikStageConfig } from '../../stages/FormikStageConfig';
import { LayoutProvider } from 'core/presentation';

export function PluginTrigger(props: any) {
  const options = [
    { label: 'Plugin Published', value: 'PUBLISHED' },
    { label: 'Preferred Version Updated', value: 'PREFERRED_VERSION_UPDATED' },
  ];

  return (
    <FormikStageConfig
      application={props.application}
      stage={props.trigger}
      pipeline={props.pipeline}
      onChange={props.triggerUpdated}
      render={() => (
        <LayoutProvider value={StandardFieldLayout}>
          <FormikFormField
            name="pluginEventType"
            label="Plugin Event"
            touched={true}
            input={(props) => <SelectInput {...props} defaultValue="PUBLISHED" options={options} />}
          />
        </LayoutProvider>
      )}
    />
  );
}
