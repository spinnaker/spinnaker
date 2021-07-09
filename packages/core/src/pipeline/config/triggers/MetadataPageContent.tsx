import React from 'react';

import { IPipeline, IPipelineTag } from '../../../domain';
import { HelpField } from '../../../help';
import { IOverridableProps, overridableComponent } from '../../../overrideRegistry';
import {
  createFakeReactSyntheticEvent,
  FormField,
  IFormInputProps,
  TextAreaInput,
  ValidationMessage,
} from '../../../presentation';

export interface IMetadataPageContentProps {
  pipeline: IPipeline;
  updatePipelineConfig: (changes: Partial<IPipeline>) => void;
}

export interface ITagsInput extends IFormInputProps {
  value?: IPipelineTag[];
}

function TagsInput({ name, onChange, value, validation }: ITagsInput) {
  const errors: string[] = (validation && (validation.messageNode as string[])) || [];
  const handleChange = (tag: IPipelineTag, index: number) => {
    const newTags = value.map((x, i) => (i === index ? tag : x));
    onChange(createFakeReactSyntheticEvent({ name, value: newTags }));
  };
  const handleDelete = (index: number) => {
    const newTags = value.filter((_x, i) => i !== index);
    onChange(createFakeReactSyntheticEvent({ name, value: newTags }));
  };
  const handleAddPair = () => {
    const newTags = (value || []).concat({ name: '', value: '' });
    onChange(createFakeReactSyntheticEvent({ name, value: newTags }));
  };

  return (
    <div>
      <table className={`table table-condensed packed tags no-border-top`}>
        <thead>
          <tr>
            <th>Name</th>
            <th>Value</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(value || []).map((tag, i) => (
            <React.Fragment key={i}>
              <tr>
                <td>
                  <input
                    className="form-control input input-sm"
                    type="text"
                    value={tag.name}
                    onChange={(e) => handleChange({ name: e.target.value, value: tag.value }, i)}
                  />
                </td>
                <td>
                  <input
                    className="form-control input input-sm"
                    type="text"
                    value={tag.value}
                    onChange={(e) => handleChange({ name: tag.name, value: e.target.value }, i)}
                  />
                </td>
                <td>
                  <div className="form-control-static">
                    <a className="clickable button" onClick={() => handleDelete(i)}>
                      <span className="glyphicon glyphicon-trash" />
                      <span className="sr-only">Remove field</span>
                    </a>
                  </div>
                </td>
              </tr>
              {errors[i] && (
                <tr>
                  <td colSpan={3}>
                    <ValidationMessage message={errors[i]} type={'error'} />
                  </td>
                </tr>
              )}
            </React.Fragment>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={3}>
              <button type="button" className="btn btn-block btn-sm add-new" onClick={handleAddPair}>
                <span className="glyphicon glyphicon-plus-sign" />
                Add tag
              </button>
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

export function MetadataPage(props: IMetadataPageContentProps) {
  const { pipeline, updatePipelineConfig } = props;

  return (
    <>
      <FormField
        name="description"
        label="Description"
        value={pipeline.description}
        onChange={(e) => updatePipelineConfig({ description: e.target.value })}
        input={(inputProps) => (
          <TextAreaInput
            {...inputProps}
            placeholder={
              '(Optional) anything that might be helpful to explain the purpose of this pipeline; Markdown is okay'
            }
            rows={3}
          />
        )}
      />
      <FormField
        name="tags"
        label="Tags"
        value={pipeline.tags}
        onChange={(e) => updatePipelineConfig({ tags: e.target.value })}
        help={<HelpField id="pipeline.config.tags" />}
        input={(inputProps) => <TagsInput {...inputProps} />}
      />
    </>
  );
}

export const MetadataPageContent = overridableComponent<
  IMetadataPageContentProps & IOverridableProps,
  typeof MetadataPage
>(MetadataPage, 'metadataPageContent');
