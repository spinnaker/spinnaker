import React from 'react';

import { IVariableMetadata } from './PipelineTemplateReader';
import { HelpField } from '../../../help/HelpField';
import { Placement } from '../../../presentation';

export interface IVariableMetadataHelpFieldProps {
  metadata: IVariableMetadata;
}

export class VariableMetadataHelpField extends React.Component<IVariableMetadataHelpFieldProps> {
  public render() {
    const content = this.getContent();
    const placement: Placement = content.split('\n').length > 10 ? 'left' : 'top';
    return <HelpField content={content} placement={placement} />;
  }

  private getContent(): string {
    let content = '';
    if (this.props.metadata.description) {
      content += `<p>${this.props.metadata.description}</p>`;
    }

    content += `<p><strong>Type:</strong> <span>${this.props.metadata.type}</span></p>`;

    if (this.props.metadata.example) {
      content += `<p><strong>Example:</strong> <br> <pre class="small">${this.props.metadata.example}</pre></p>`;
    }
    return content;
  }
}
