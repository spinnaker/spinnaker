import React from 'react';
import { ToastContainer } from 'react-toastify';

import './notifier.component.less';
import 'react-toastify/dist/ReactToastify.min.css';

export const Notifier = () => {
  return <ToastContainer position="bottom-right" autoClose={false} newestOnTop={true} closeOnClick={false} />;
};
