import { cloneDeep, has } from 'lodash';
import React from 'react';

import { ArtifactTypePatterns } from '../../../../../artifact';
import { IArtifactEditorProps, IArtifactKindConfig } from '../../../../../domain';
import { singleFieldArtifactEditor } from '../singleFieldArtifactEditor';
import { StageConfigField } from '../../../stages/common';
import { CopyToClipboard } from '../../../../../utils';
import { SpelText } from '../../../../../widgets';

export const BASE_64_ARTIFACT_TYPE = 'embedded/base64';
export const BASE_64_ARTIFACT_ACCOUNT = 'embedded-artifact';

interface IDefaultBase64ArtifactEditorState {
  decoded: string;
  encodeDecodeError: string;
}

class DefaultBase64ArtifactEditor extends React.Component<IArtifactEditorProps, IDefaultBase64ArtifactEditorState> {
  private static DOMBase64Errors: { [key: string]: string } = {
    5: 'The string to encode contains characters outside the latin1 range.',
  };

  constructor(props: IArtifactEditorProps) {
    super(props);
    if (props.artifact.type !== BASE_64_ARTIFACT_TYPE) {
      const clonedArtifact = cloneDeep(props.artifact);
      clonedArtifact.type = BASE_64_ARTIFACT_TYPE;
      props.onChange(clonedArtifact);
    }
    const [decoded, encodeDecodeError] = this.convert(atob, props.artifact.reference);
    this.state = { decoded: decoded, encodeDecodeError: encodeDecodeError };
  }

  private convert = (fn: (s: string) => string, str: string): [string, string] => {
    if (!str || str.length === 0) {
      return [str, ''];
    }

    try {
      const converted = fn(str);
      return [converted, ''];
    } catch (e) {
      if (has(DefaultBase64ArtifactEditor.DOMBase64Errors, e.code)) {
        return ['', DefaultBase64ArtifactEditor.DOMBase64Errors[e.code]];
      } else {
        return ['', e.message];
      }
    }
  };

  private onNameChanged = (name: string) => {
    const artifact = cloneDeep(this.props.artifact);
    artifact.name = name;
    this.props.onChange(artifact);
  };

  private onContentChanged = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    const [encoded, encodeDecodeError] = this.convert(btoa, event.target.value);
    if (!encodeDecodeError) {
      const artifact = cloneDeep(this.props.artifact);
      artifact.reference = encoded;
      this.props.onChange(artifact);
    }
    this.setState({
      decoded: event.target.value,
      encodeDecodeError: encodeDecodeError,
    });
  };

  public render() {
    const { pipeline } = this.props;
    const { name, reference } = this.props.artifact;
    const { decoded, encodeDecodeError } = this.state;
    return (
      <>
        <StageConfigField label="Name">
          <SpelText
            placeholder="base64-artifact"
            value={name}
            pipeline={pipeline}
            onChange={this.onNameChanged}
            docLink={false}
          />
        </StageConfigField>
        <StageConfigField label="Contents">
          <textarea
            autoCapitalize="none"
            autoComplete="off"
            rows={16}
            className="form-control code"
            value={decoded}
            onChange={this.onContentChanged}
          />
          <CopyToClipboard
            text={reference}
            toolTip="Copy base64-encoded content to clipboard"
            analyticsLabel="Copy Base64 Artifact Content"
          />
        </StageConfigField>
        {encodeDecodeError && (
          <div className="form-group row">
            <div className="col-md-12 error-message">Error encoding/decoding artifact content: {encodeDecodeError}</div>
          </div>
        )}
      </>
    );
  }
}

export const Base64Match: IArtifactKindConfig = {
  label: 'Base64',
  typePattern: ArtifactTypePatterns.EMBEDDED_BASE64,
  type: BASE_64_ARTIFACT_TYPE,
  description: 'An artifact that includes its referenced resource as part of its payload.',
  key: 'base64',
  isDefault: false,
  isMatch: true,
  editCmp: singleFieldArtifactEditor('name', BASE_64_ARTIFACT_TYPE, 'Name', 'base64-artifact', ''),
};

export const Base64Default: IArtifactKindConfig = {
  label: 'Base64',
  typePattern: ArtifactTypePatterns.EMBEDDED_BASE64,
  type: BASE_64_ARTIFACT_TYPE,
  description: 'An artifact that includes its referenced resource as part of its payload.',
  key: 'default.base64',
  isDefault: true,
  isMatch: false,
  editCmp: DefaultBase64ArtifactEditor,
};
