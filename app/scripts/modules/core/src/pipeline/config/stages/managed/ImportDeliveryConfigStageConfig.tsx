import React from 'react';

import { SETTINGS } from 'core/config/settings';
import { HelpField } from 'core/help';
import { FormikFormField, TextInput } from 'core/presentation';

import { FormikStageConfig } from '../FormikStageConfig';
import { IStageConfigProps } from '../common';

export const ImportDeliveryConfigStageConfig: React.ComponentType<IStageConfigProps> = (stageConfigProps) => (
  <FormikStageConfig
    {...stageConfigProps}
    onChange={stageConfigProps.updateStage}
    render={() => (
      <div className="form-horizontal">
        <FormikFormField
          name="manifest"
          label="Manifest Path"
          help={<HelpField id="pipeline.config.deliveryConfig.manifest" />}
          input={(props) => (
            <TextInput
              {...props}
              prefix={SETTINGS.managedDelivery?.manifestBasePath + '/'}
              placeholder={SETTINGS.managedDelivery?.defaultManifest}
            />
          )}
        />
      </div>
    )}
  />
);
