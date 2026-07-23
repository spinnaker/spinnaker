import React from 'react';

import { toMarkdown } from '../Markdown';
import { ModalBody } from './ModalBody';
import { ModalFooter } from './ModalFooter';
import { ModalHeader } from './ModalHeader';
import type { IModalComponentProps } from './showModal';
import { showModal } from './showModal';

export interface IErrorModalProps {
  buttonText?: string;
  header?: string;
  body: string | JSX.Element;
}

const ErrorModal = ({
  body,
  buttonText = 'Close',
  dismissModal,
  header = 'Something went wrong :(',
}: IErrorModalProps & IModalComponentProps) => (
  <>
    <ModalHeader>{header}</ModalHeader>
    <ModalBody>{React.isValidElement(body) ? body : toMarkdown(body as string)}</ModalBody>
    <ModalFooter
      primaryActions={
        <button className="btn btn-default" type="button" onClick={() => dismissModal()}>
          {buttonText}
        </button>
      }
    />
  </>
);

export class ErrorModalService {
  public static error(params: IErrorModalProps): PromiseLike<any> {
    return showModal(ErrorModal, params);
  }
}
