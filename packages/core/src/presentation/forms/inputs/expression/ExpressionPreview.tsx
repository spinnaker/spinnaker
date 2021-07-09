import React from 'react';

import { Markdown } from '../../../Markdown';

export interface IExpressionPreviewProps {
  spelPreview: string;
  markdown: boolean;
}

export const ExpressionPreview = ({ spelPreview, markdown }: IExpressionPreviewProps) => (
  <div className="flex-container-h baseline margin-between-lg">
    <span className="no-grow">Preview:</span>{' '}
    {markdown ? <Markdown message={spelPreview} /> : <span>{spelPreview}</span>}
  </div>
);
