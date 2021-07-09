/* eslint-disable @spinnaker/import-sort */
import React from 'react';
import AceEditor, { AceEditorProps, Annotation } from 'react-ace';

import 'brace/mode/json';
import 'brace/ext/searchbox';
import './aceEditor.less';

export interface IJsonEditorProps extends AceEditorProps {
  value: string;
  onChange?: (json: string) => void;
  onValidation?: (message: string) => void;
  readOnly?: boolean;
  autofocus?: boolean;
}

export class JsonEditor extends React.Component<IJsonEditorProps> {
  public static defaultProps: Partial<IJsonEditorProps> = {
    mode: 'json',
    theme: 'textmate',
    style: { width: '100%', border: '1px solid var(--color-concrete)' },
    fontSize: 11,
    showGutter: true,
    showPrintMargin: true,
    highlightActiveLine: true,
    className: 'ace-editor flex-fill',
    autofocus: true,
  };

  private editorRef = React.createRef<AceEditor>();

  private validate = (annotations: Annotation[]): void => {
    const { onValidation } = this.props;
    if (!onValidation) {
      return;
    }
    if (!annotations || !annotations.length) {
      onValidation(null);
    }
    const errors = annotations.map((a) => {
      return `Line ${a.row + 1}, column ${a.column + 1}: ${a.text}`;
    });
    onValidation(errors.join('; '));
  };

  public componentDidMount(): void {
    if (this.props.autofocus) {
      const { editor } = this.editorRef.current as any;
      editor.focus();
      editor.navigateFileStart();
    }
  }

  public render() {
    return (
      <AceEditor
        {...this.props}
        ref={this.editorRef}
        onValidate={this.validate}
        editorProps={{ $blockScrolling: Infinity }}
      />
    );
  }
}
