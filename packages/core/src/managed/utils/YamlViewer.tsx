import React from 'react';
import AceEditor from 'react-ace';

export interface IYamlViewerProps {
  content: string;
}

export const YamlViewer: React.FC<IYamlViewerProps> = ({ content }: IYamlViewerProps) => {
  return (
    <div className="full-width">
      <div className="sp-margin-xl-bottom">
        <AceEditor
          mode="yaml"
          theme="textmate"
          readOnly
          fontSize={12}
          cursorStart={0}
          showPrintMargin={false}
          highlightActiveLine={true}
          maxLines={Infinity}
          value={content}
          setOptions={{
            firstLineNumber: 1,
            tabSize: 2,
            showLineNumbers: true,
            showFoldWidgets: true,
          }}
          style={{ width: 'auto' }}
          className="ace-editor sp-margin-m-top"
          editorProps={{ $blockScrolling: true }}
          onLoad={(editor) => {
            // This removes the built-in search box (as it doesn't scroll properly to matches)
            // commands is missing in the type def and therefore we have to cast as any
            (editor as any).commands?.removeCommand('find');
          }}
        />
      </div>
    </div>
  );
};
