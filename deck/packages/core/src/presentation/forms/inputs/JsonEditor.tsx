/* eslint-disable @spinnaker/import-sort */
import { loadAll, YAMLException } from 'js-yaml';
import React from 'react';
import type { IAceEditorProps, IAnnotation } from 'react-ace';
import AceEditor from 'react-ace';

import 'ace-builds/src-noconflict/mode-json';
import 'ace-builds/src-noconflict/theme-textmate';
import 'ace-builds/src-noconflict/ext-searchbox';
import './aceEditor.less';

export interface IJsonEditorProps extends IAceEditorProps {
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

  // JSON is a subset of YAML, so js-yaml gives us a parser with structured
  // line/column error info without needing Ace's json_worker (which requires
  // extra webpack wiring to serve as a separate script).
  private calculateErrors = (value: string): IAnnotation[] => {
    if (!value) {
      return [];
    }
    try {
      loadAll(value, null);
    } catch (e) {
      if (e instanceof YAMLException) {
        const mark = (e as any).mark;
        return [
          {
            column: mark ? mark.column : 0,
            row: mark ? mark.line : 0,
            type: 'error',
            text: e.message,
          },
        ];
      }
    }
    return [];
  };

  private validate = (annotations: IAnnotation[]): void => {
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
        annotations={this.calculateErrors(this.props.value)}
        setOptions={{ ...this.props.setOptions, useWorker: false }}
        editorProps={{ $blockScrolling: Infinity }}
      />
    );
  }
}
