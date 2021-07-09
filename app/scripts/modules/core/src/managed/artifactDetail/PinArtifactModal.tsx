import React, { memo, useCallback, useEffect } from 'react';
import { Option } from 'react-select';

import { Illustration } from '@spinnaker/presentation';

import { Button } from '../Button';
import { EnvironmentBadge } from '../EnvironmentBadge';
import { ManagedWriter } from '../ManagedWriter';
import { Application } from '../../application';
import { getArtifactVersionDisplayName } from '../displayNames';
import { IManagedArtifactVersion, IManagedResourceSummary } from '../../domain';
import { HelpField } from '../../help';
import {
  FormikFormField,
  IModalComponentProps,
  ModalBody,
  ModalFooter,
  ModalHeader,
  ReactSelectInput,
  showModal,
  SpinFormik,
  TextAreaInput,
  ValidationMessage,
} from '../../presentation';
import { useEnvironmentTypeFromResources } from '../useEnvironmentTypeFromResources.hooks';
import { logger } from '../../utils';

const PINNING_DOCS_URL = 'https://www.spinnaker.io/guides/user/managed-delivery/pinning';

const logEvent = (label: string, application: string, environment?: string, reference?: string) =>
  logger.log({
    category: 'Environments - pin version modal',
    action: label,
    data: { label: environment ? `${application}:${environment}:${reference}` : application },
  });

type IEnvironmentOptionProps = Required<Option<string>> & {
  disabledReason: string;
  allResources: IManagedResourceSummary[];
};

const EnvironmentOption = memo(({ label, disabled, disabledReason, allResources }: IEnvironmentOptionProps) => {
  const isCritical = useEnvironmentTypeFromResources(allResources);

  return (
    <span>
      <span className="sp-margin-s-right" style={{ opacity: disabled ? 0.66 : 1 }}>
        <EnvironmentBadge name={label} critical={isCritical} size="extraSmall" />
      </span>
      {disabled && (
        <span className="small" style={{ fontStyle: 'italic', color: '#666' }}>
          Already {disabledReason} here
        </span>
      )}
    </span>
  );
});

export interface IPinArtifactModalProps extends IModalComponentProps {
  application: Application;
  reference: string;
  version: IManagedArtifactVersion;
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
}

export const showPinArtifactModal = (props: IPinArtifactModalProps) =>
  showModal(PinArtifactModal, props, { maxWidth: 750 }).then((result) => {
    if (result.status === 'DISMISSED') {
      logEvent('Modal dismissed', props.application.name);
    }
    return result;
  });

export const PinVersionIntro = ({ application }: { application: string }) => {
  return (
    <div className="flex-container-h middle sp-margin-xl-bottom">
      <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
        <Illustration name="pinArtifactVersion" />
      </span>
      <span>
        <p>
          Pinning ensures an environment uses a specific version, even if Spinnaker would've normally deployed a
          different one. If you pin a version, it'll remain pinned until you manually unpin it.
        </p>{' '}
        <a target="_blank" onClick={() => logEvent('Pinning docs link clicked', application)} href={PINNING_DOCS_URL}>
          Check out our documentation
        </a>{' '}
        for more information.
      </span>
    </div>
  );
};

export const PinArtifactModal = memo(
  ({ application, reference, version, resourcesByEnvironment, dismissModal, closeModal }: IPinArtifactModalProps) => {
    const optionRenderer = useCallback(
      (option: Required<Option<string>> & { disabledReason: string }) => (
        <EnvironmentOption {...option} allResources={resourcesByEnvironment[option.value]} />
      ),
      [resourcesByEnvironment],
    );

    useEffect(() => logEvent('Modal seen', application.name), []);

    return (
      <>
        <ModalHeader>Pin {getArtifactVersionDisplayName(version)}</ModalHeader>
        <SpinFormik<{
          environment?: string;
          comment?: string;
        }>
          initialValues={{
            environment: version.environments.find(({ pinned, state }) => !pinned && state !== 'vetoed')?.name,
          }}
          onSubmit={({ environment, comment }, { setSubmitting, setStatus }) => {
            environment &&
              comment &&
              ManagedWriter.pinArtifactVersion({
                environment,
                reference,
                comment,
                application: application.name,
                version: version.version,
              })
                .then(() => {
                  logEvent('Version pinned', application.name, environment, reference);
                  closeModal?.();
                })
                .catch((error: { data: { error: string; message: string } }) => {
                  setSubmitting(false);
                  setStatus({ error: error.data });
                  logEvent('Error pinning version', application.name, environment, reference);
                });
          }}
          render={({ status, isValid, isSubmitting, submitForm }) => {
            const errorTitle = status?.error?.error;
            const errorMessage = status?.error?.message;

            return (
              <>
                <ModalBody>
                  <div className="flex-container-v middle sp-padding-xl-yaxis">
                    <PinVersionIntro application={application.name} />
                    <FormikFormField
                      name="environment"
                      label="Environment"
                      input={(props) => (
                        <ReactSelectInput
                          {...props}
                          options={version.environments.map(({ name, pinned, state }) => ({
                            label: name,
                            value: name,
                            disabled: !!pinned || state === 'vetoed',
                            disabledReason: !!pinned ? 'pinned' : 'marked as bad',
                          }))}
                          optionRenderer={optionRenderer}
                          valueRenderer={optionRenderer}
                          searchable={false}
                          clearable={false}
                        />
                      )}
                    />
                    <FormikFormField
                      label="Reason"
                      name="comment"
                      required={true}
                      input={(props) => <TextAreaInput {...props} rows={5} required={true} />}
                    />
                    <div className="small text-right">
                      Markdown is okay <HelpField id="markdown.examples" />
                    </div>

                    {status?.error && (
                      <div className="sp-margin-xl-top">
                        <ValidationMessage
                          type="error"
                          message={
                            <span className="flex-container-v">
                              <span className="text-bold">Something went wrong:</span>
                              {errorTitle && <span className="text-semibold">{errorTitle}</span>}
                              {errorMessage && <span>{errorMessage}</span>}
                            </span>
                          }
                        />
                      </div>
                    )}
                  </div>
                </ModalBody>
                <ModalFooter
                  primaryActions={
                    <div className="flex-container-h sp-group-margin-s-xaxis">
                      <Button onClick={() => dismissModal?.()}>Cancel</Button>
                      <Button appearance="primary" disabled={!isValid || isSubmitting} onClick={() => submitForm()}>
                        Pin
                      </Button>
                    </div>
                  }
                />
              </>
            );
          }}
        />
      </>
    );
  },
);
