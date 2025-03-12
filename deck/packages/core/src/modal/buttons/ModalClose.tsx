import React from 'react';

export const ModalClose = ({ dismiss }: { dismiss: () => void }) => (
  <div className="modal-close close-button pull-right">
    <button className="link" type="button" onClick={dismiss}>
      <span className="glyphicon glyphicon-remove" />
    </button>
  </div>
);
