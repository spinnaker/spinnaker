import * as React from 'react';

import { ConsoleOutputModal } from './ConsoleOutputModal';
import { IInstance } from '../../../domain';
import { showModal } from '../../../presentation';

export interface IConsoleOutputProps {
  instance: IInstance;
  text?: string;
  usesMultiOutput?: boolean;
}

export const ConsoleOutputLink = ({ instance, text, usesMultiOutput }: IConsoleOutputProps) => {
  const showConsoleOutput = () => {
    showModal(ConsoleOutputModal, { instance, usesMultiOutput });
  };

  return (
    <button className="btn btn-link" onClick={showConsoleOutput}>
      {text || 'Console Output (Raw)'}
    </button>
  );
};
