import { isEqual, isPlainObject } from 'lodash';
import React from 'react';

import type { IWizardPageComponent } from '@spinnaker/core';

import type {
  GceCommandHandlerName,
  GceConfigurationRefreshMethod,
  GceConfigurationUpdateMethod,
  IGceServerGroupCommand,
  IGceServerGroupWizardCommandState,
  IGceServerGroupWizardFieldOrigin,
  IGceServerGroupWizardPageProps,
} from './GceServerGroupWizard.types';
import { GceServerGroupWizardAdapter } from './GceServerGroupWizardAdapter';

export function createGceServerGroupWizardCommandState(
  command: IGceServerGroupCommand,
): IGceServerGroupWizardCommandState {
  return { command, fieldOrigins: {}, formikValues: command, requestGeneration: 0 };
}

export abstract class GceServerGroupWizardPage<
    P extends IGceServerGroupWizardPageProps = IGceServerGroupWizardPageProps
  >
  extends React.Component<P>
  implements IWizardPageComponent<IGceServerGroupCommand> {
  protected readonly adapter = this.props.adapter || new GceServerGroupWizardAdapter();
  private readonly commandState =
    this.props.commandState || createGceServerGroupWizardCommandState(this.props.formik.values);

  private requestGeneration = 0;
  private isUnmounted = false;

  public validate(_values: IGceServerGroupCommand): { [key: string]: any } {
    return {};
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
    this.requestGeneration++;
  }

  protected configureCommand(): Promise<IGceServerGroupCommand | undefined> {
    return this.runLatestCommandRequest(this.props.formik.values, (command) =>
      this.adapter.configureCommand(this.props.app, command),
    );
  }

  protected applyCommandHandler(handler: GceCommandHandlerName): Promise<IGceServerGroupCommand | undefined> {
    return this.runLatestCommandRequest(this.props.formik.values, async (command) => {
      const update = await this.adapter.applyCommandHandler(command, handler);
      return update.command;
    });
  }

  protected applyConfigurationUpdate(
    method: GceConfigurationUpdateMethod,
  ): Promise<IGceServerGroupCommand | undefined> {
    return this.runLatestCommandRequest(this.props.formik.values, async (command) => {
      const update = await this.adapter.applyConfigurationUpdate(command, method);
      return update.command;
    });
  }

  protected applyConfigurationRefresh(
    method: GceConfigurationRefreshMethod,
    skipCommandReconfiguration?: boolean,
  ): Promise<IGceServerGroupCommand | undefined> {
    return this.runLatestCommandRequest(this.props.formik.values, async (command) => {
      const update = await this.adapter.applyConfigurationRefresh(command, method, skipCommandReconfiguration);
      return update.command;
    });
  }

  protected async runLatestCommandRequest(
    requestedCommand: IGceServerGroupCommand,
    request: (command: IGceServerGroupCommand) => Promise<IGceServerGroupCommand>,
  ): Promise<IGceServerGroupCommand | undefined> {
    const pageGeneration = ++this.requestGeneration;
    this.syncFormikValues();
    const commandGeneration = ++this.commandState.requestGeneration;
    const requestCommand = mergeCommandChanges(
      this.props.formik.values,
      requestedCommand,
      this.commandState.command,
      this.commandState.fieldOrigins,
      { generation: commandGeneration, source: 'user' },
      true,
    );
    this.commandState.command = requestCommand;
    this.props.onLoadingChanged?.(true);
    try {
      const command = await request(requestCommand);
      if (this.isUnmounted) {
        return undefined;
      }
      this.syncFormikValues();
      const currentCommand = this.commandState.command;
      const mergedCommand = mergeCommandChanges(
        requestCommand,
        command,
        currentCommand,
        this.commandState.fieldOrigins,
        { generation: commandGeneration, source: 'result' },
      );
      if (!isEqual(mergedCommand, currentCommand) || commandGeneration === this.commandState.requestGeneration) {
        this.commandState.command = mergedCommand;
        this.props.formik.setValues(mergedCommand);
      }
      return mergedCommand;
    } finally {
      if (!this.isUnmounted && pageGeneration === this.requestGeneration) {
        this.props.onLoadingChanged?.(false);
      }
    }
  }

  private syncFormikValues(): void {
    const { formikValues } = this.commandState;
    if (formikValues === this.props.formik.values) {
      return;
    }
    this.commandState.command = mergeCommandChanges(
      formikValues,
      this.props.formik.values,
      this.commandState.command,
      this.commandState.fieldOrigins,
      { generation: this.commandState.requestGeneration, source: 'user' },
    );
    this.commandState.formikValues = this.props.formik.values;
  }
}

function mergeCommandChanges(
  base: IGceServerGroupCommand,
  changed: IGceServerGroupCommand,
  current: IGceServerGroupCommand,
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>,
  incomingOrigin: IGceServerGroupWizardFieldOrigin,
  markMatchingChanges = false,
): IGceServerGroupCommand {
  return mergeValueChanges(
    base,
    changed,
    current,
    fieldOrigins,
    incomingOrigin,
    [],
    markMatchingChanges,
  ) as IGceServerGroupCommand;
}

function mergeValueChanges(
  base: any,
  changed: any,
  current: any,
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>,
  incomingOrigin: IGceServerGroupWizardFieldOrigin,
  path: string[],
  markMatchingChanges: boolean,
): any {
  if (isEqual(changed, base)) {
    return current;
  }
  if (isEqual(changed, current)) {
    if (markMatchingChanges) {
      recordChangedOrigins(base, changed, fieldOrigins, incomingOrigin, path);
    }
    return current;
  }

  if (
    isPlainObject(changed) &&
    (isPlainObject(base) || base === undefined) &&
    (isPlainObject(current) || current === undefined)
  ) {
    const baseObject = base || {};
    const currentObject = current || {};
    const result = { ...currentObject };
    new Set([...Object.keys(baseObject), ...Object.keys(changed)]).forEach((key) => {
      const childPath = [...path, key];
      if (!Object.prototype.hasOwnProperty.call(changed, key)) {
        if (
          isEqual(currentObject[key], baseObject[key]) ||
          incomingWinsConflict(fieldOrigins, incomingOrigin, childPath)
        ) {
          delete result[key];
          recordFieldOrigin(fieldOrigins, incomingOrigin, childPath);
        }
        return;
      }
      result[key] = mergeValueChanges(
        baseObject[key],
        changed[key],
        currentObject[key],
        fieldOrigins,
        incomingOrigin,
        childPath,
        markMatchingChanges,
      );
    });
    return result;
  }

  if (isEqual(current, base) || incomingWinsConflict(fieldOrigins, incomingOrigin, path)) {
    recordFieldOrigin(fieldOrigins, incomingOrigin, path);
    return changed;
  }
  return current;
}

function incomingWinsConflict(
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>,
  incomingOrigin: IGceServerGroupWizardFieldOrigin,
  path: string[],
): boolean {
  if (incomingOrigin.source === 'user') {
    return true;
  }
  const currentOrigin = findFieldOrigin(fieldOrigins, path);
  return currentOrigin?.source === 'result' && incomingOrigin.generation > currentOrigin.generation;
}

function findFieldOrigin(
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>,
  path: string[],
): IGceServerGroupWizardFieldOrigin | undefined {
  for (let length = path.length; length >= 0; length--) {
    const origin = fieldOrigins[fieldOriginKey(path.slice(0, length))];
    if (origin) {
      return origin;
    }
  }
  return undefined;
}

function recordChangedOrigins(
  base: any,
  changed: any,
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>,
  incomingOrigin: IGceServerGroupWizardFieldOrigin,
  path: string[],
): void {
  if (isEqual(base, changed)) {
    return;
  }
  if (isPlainObject(changed) && (isPlainObject(base) || base === undefined)) {
    const baseObject = base || {};
    new Set([...Object.keys(baseObject), ...Object.keys(changed)]).forEach((key) => {
      const childPath = [...path, key];
      if (!Object.prototype.hasOwnProperty.call(changed, key)) {
        recordFieldOrigin(fieldOrigins, incomingOrigin, childPath);
        return;
      }
      recordChangedOrigins(baseObject[key], changed[key], fieldOrigins, incomingOrigin, childPath);
    });
    return;
  }
  recordFieldOrigin(fieldOrigins, incomingOrigin, path);
}

function recordFieldOrigin(
  fieldOrigins: Record<string, IGceServerGroupWizardFieldOrigin>,
  incomingOrigin: IGceServerGroupWizardFieldOrigin,
  path: string[],
): void {
  const key = fieldOriginKey(path);
  const descendantPrefix = `${key}\u0000`;
  Object.keys(fieldOrigins).forEach((originKey) => {
    if (originKey.startsWith(descendantPrefix)) {
      delete fieldOrigins[originKey];
    }
  });
  fieldOrigins[key] = incomingOrigin;
}

function fieldOriginKey(path: string[]): string {
  return path.join('\u0000');
}
