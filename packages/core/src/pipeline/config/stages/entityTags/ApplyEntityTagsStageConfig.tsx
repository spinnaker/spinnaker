import React from 'react';

import { TagEditor } from './TagEditor';
import { IStageConfigProps, StageConfigField } from '../common';
import { SETTINGS } from '../../../../config';
import { IEntityRef, IEntityTag, IStage } from '../../../../domain';
import { FormField, ReactSelectInput, TextInput } from '../../../../presentation';

export interface IApplyEntityTagsStage extends IStage {
  entityRef: IEntityRef;
  tags: IEntityTag[];
}

export interface IApplyEntityTagsStageConfigProps extends IStageConfigProps {
  stage: IApplyEntityTagsStage;
}

export class ApplyEntityTagsStageConfig extends React.Component<IApplyEntityTagsStageConfigProps> {
  private entityOptions = [
    { label: 'Server Group', value: 'servergroup' },
    { label: 'Load Balancer', value: 'loadbalancer' },
    { label: 'Application', value: 'application' },
    { label: 'Cluster', value: 'cluster' },
    { label: 'Security Group', value: 'securitygroup' },
  ];

  private entityRefTypeChanged = (entityType: string) => {
    const entityRef = { entityType };
    this.props.updateStageField({ entityRef });
  };

  private entityRefFieldChanged = (key: string, value: string) => {
    const entityRef = { ...this.props.stage.entityRef, [key]: value };
    this.props.updateStageField({ entityRef });
  };

  private tagChanged = (tag: IEntityTag, index: number) => {
    const tags = this.props.stage.tags.slice();
    tags[index] = tag;
    this.props.updateStageField({ tags });
  };

  private addTag = () => {
    this.props.updateStageField({ tags: this.props.stage.tags.concat({ name: '', value: '' }) });
  };

  public render() {
    const {
      stage: { entityRef = {} as IEntityRef, tags = [] as IEntityTag[] },
      application: {
        attributes: { cloudProviders = SETTINGS.defaultProviders },
      },
    } = this.props;

    return (
      <div className="form-horizontal">
        <StageConfigField label="Entity Type">
          <FormField
            input={(inputProps) => (
              <ReactSelectInput {...inputProps} options={this.entityOptions} clearable={false} className="full-width" />
            )}
            onChange={(e: any) => this.entityRefTypeChanged(e.target.value)}
            value={entityRef.entityType || ''}
          />
        </StageConfigField>
        {entityRef.entityType && (
          <>
            <StageConfigField label="Name">
              <FormField
                input={(props) => <TextInput {...props} />}
                value={entityRef.entityId || ''}
                onChange={(e: any) => this.entityRefFieldChanged('entityId', e.target.value)}
              />
            </StageConfigField>
            {entityRef.entityType !== 'application' && (
              <>
                <StageConfigField label="Cloud Provider">
                  <FormField
                    input={(inputProps) => (
                      <ReactSelectInput
                        {...inputProps}
                        stringOptions={cloudProviders}
                        clearable={false}
                        className="full-width"
                      />
                    )}
                    value={entityRef.cloudProvider || ''}
                    onChange={(e: any) => this.entityRefFieldChanged('cloudProvider', e.target.value)}
                  />
                </StageConfigField>
                <StageConfigField label="Account">
                  <FormField
                    input={(props) => <TextInput {...props} />}
                    value={entityRef.account || ''}
                    onChange={(e: any) => this.entityRefFieldChanged('account', e.target.value)}
                  />
                </StageConfigField>
                <StageConfigField label="Region" helpKey="pipeline.config.entitytags.region">
                  <FormField
                    input={(props) => <TextInput {...props} />}
                    value={entityRef.region || ''}
                    onChange={(e: any) => this.entityRefFieldChanged('region', e.target.value)}
                  />
                </StageConfigField>
                {entityRef.entityType === 'securitygroup' && (
                  <StageConfigField label="VPC Id">
                    <FormField
                      input={(props) => <TextInput {...props} />}
                      value={entityRef.vpcId || ''}
                      onChange={(e: any) => this.entityRefFieldChanged('vpcId', e.target.value)}
                    />
                  </StageConfigField>
                )}
              </>
            )}
          </>
        )}
        <StageConfigField label="Tags">
          {tags.map((tag, index) => (
            <TagEditor key={index} tag={tag} onChange={(t) => this.tagChanged(t, index)} />
          ))}
          <button type="button" className="add-new col-md-12" onClick={this.addTag}>
            <span className="glyphicon glyphicon-plus-sign" /> Add new tag
          </button>
        </StageConfigField>
      </div>
    );
  }
}
