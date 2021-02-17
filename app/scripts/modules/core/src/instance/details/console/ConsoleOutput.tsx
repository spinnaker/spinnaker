import * as React from 'react';

import { CollapsibleSection, showModal } from '../../../presentation';
import { ConsoleOutputModal } from './ConsoleOutputModal';
import { IInstance } from '../../../domain';

export interface IConsoleOutputProps {
  instance: IInstance;
  text?: string;
  usesMultiOutput?: boolean;
}

export const ConsoleOutput = ({ instance, text, usesMultiOutput }: IConsoleOutputProps) => {
  const showConsoleOutput = () => {
    showModal(ConsoleOutputModal, { instance, usesMultiOutput });
  };

  return <a onClick={showConsoleOutput}>{text || 'Console Output (Raw)'}</a>;
};
