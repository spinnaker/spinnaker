import React from 'react';
import { toast, ToastOptions } from 'react-toastify';
export interface INotifier {
  key: string;
  action?: 'create';
  content: React.ReactNode;
  options?: ToastOptions;
}

export class NotifierService {
  public static publish(message: INotifier): void {
    const existing = toast.isActive(message.key);
    if (existing) {
      toast.update(message.key, { render: message.content, ...message.options });
    } else {
      toast(message.content, { toastId: message.key, ...message.options });
    }
  }

  public static clear(key: string): void {
    toast.dismiss(key);
  }
}
