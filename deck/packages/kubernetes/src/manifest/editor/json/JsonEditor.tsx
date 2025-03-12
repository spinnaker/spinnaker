import 'brace/mode/json';
import 'brace/theme/textmate';
import { $log } from 'ngimport';
import React from 'react';
import AceEditor from 'react-ace';

export interface IJsonEditorProps {
  value: string;
  onChange(raw: string, obj: any): void;
}

export const JSON_EDITOR_TAB_SIZE = 2;

export class JsonEditor extends React.Component<IJsonEditorProps> {
  private handleChange = (raw: string) => {
    try {
      const obj = JSON.parse(raw);
      this.props.onChange
        ? this.props.onChange(raw, obj)
        : $log.warn('No `onChange` handler provided for JSON editor.');
    } catch (e) {
      this.props.onChange
        ? this.props.onChange(raw, null)
        : $log.warn('No `onChange` handler provided for JSON editor.');
      $log.warn(`Error loading JSON from string ${raw}: `, e);
    }
  };

  public render = () => {
    return (
      <AceEditor
        mode="json"
        theme="textmate"
        name="json-editor"
        style={{ width: 'inherit' }}
        onChange={this.handleChange}
        fontSize={12}
        showGutter={true}
        cursorStart={0}
        showPrintMargin={false}
        minLines={50}
        maxLines={100}
        highlightActiveLine={true}
        value={this.props.value || undefined}
        setOptions={{
          firstLineNumber: 1,
          tabSize: JSON_EDITOR_TAB_SIZE,
          showLineNumbers: false,
          showFoldWidgets: false,
        }}
        editorProps={{ $blockScrolling: Infinity }}
        className="ace-editor"
      />
    );
  };
}
