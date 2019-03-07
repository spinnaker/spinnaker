# Copyright 2019 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


"""Generate an auth token to make requests to an IAP-protected endpoint.

This file was derived from
https://github.com/GoogleCloudPlatform/python-docs-samples/blob/master/iap/make_iap_request.py
"""

import google.auth
import google.auth.app_engine
import google.auth.compute_engine.credentials
import google.auth.iam
import google.auth.impersonated_credentials
import google.oauth2.credentials
import google.oauth2.service_account
import requests


from google.auth.transport.requests import Request


OAUTH_TOKEN_URI = 'https://www.googleapis.com/oauth2/v4/token'
IAM_SCOPE = 'https://www.googleapis.com/auth/iam'


def __make_service_account_credentials(client_id, service_account_file=None, impersonate_service_account_email=None):
  if service_account_file:
    bootstrap_credentials = google.oauth2.service_account.Credentials.from_service_account_file(service_account_file)
  else:
    bootstrap_credentials, _ = google.auth.default(scopes=[IAM_SCOPE])

  if impersonate_service_account_email:
    bootstrap_credentials = google.auth.impersonated_credentials.Credentials(
        source_credentials=bootstrap_credentials,
        target_principal=impersonate_service_account_email,
        target_scopes=[IAM_SCOPE],
        lifetime=60)

  if isinstance(bootstrap_credentials,
                google.oauth2.credentials.Credentials):
    raise Exception('generate_auth_token is only supported for service '
                    'accounts.')
  elif isinstance(bootstrap_credentials,
                  google.auth.app_engine.Credentials):
    requests_toolbelt.adapters.appengine.monkeypatch()

  if impersonate_service_account_email:
    signer_email = impersonate_service_account_email
  else:
    signer_email = bootstrap_credentials.service_account_email

  if impersonate_service_account_email or isinstance(bootstrap_credentials,
      google.auth.compute_engine.credentials.Credentials):
    signer = google.auth.iam.Signer(
        Request(), bootstrap_credentials, signer_email)
  else:
    signer = bootstrap_credentials.signer

  return google.oauth2.service_account.Credentials(
      signer, signer_email, token_uri=OAUTH_TOKEN_URI, additional_claims={
          'target_audience': client_id
      })

def __get_token_from_credentials(service_account_credentials):
  service_account_jwt = (
      service_account_credentials._make_authorization_grant_assertion())
  request = google.auth.transport.requests.Request()
  body = {
    'assertion': service_account_jwt,
    'grant_type': google.oauth2._client._JWT_GRANT_TYPE,
  }
  token_response = google.oauth2._client._token_endpoint_request(
      request, OAUTH_TOKEN_URI, body)
  return token_response['id_token']


def generate_auth_token(client_id, service_account_file=None, impersonate_service_account_email=None):
  """Generates an auth token to make requests to an IAP-protected endpoint.

  Args:
    client_id: The client ID used by IAP to generate the token.
    service_account_file: [string] The path to a credentials file for a service
       account that can be used to generate the token. If service_account_file
       is None, the service account used will be based on the Application
       Default Credentials.
    impersonate_service_account_email: [string] The service account name (email)
       to impersonate to request the bearer token. If
       impersonate_service_account_email is None, no service account will be
       impersonated.
  """
  service_account_credentials =__make_service_account_credentials(client_id, service_account_file, impersonate_service_account_email)
  return __get_token_from_credentials(service_account_credentials)

def get_service_account_email(service_account_file=None):
  """Parse a service account email from a service_account_file or ADC.

  Args:
    service_account_file: [string] The path to a credentials file for a service
       account. If service_account_file is None, the service account used will
       be based on the Application Default Credentials.
  """
  if service_account_file:
    credentials = google.oauth2.service_account.Credentials.from_service_account_file(service_account_file)
  else:
    credentials, _ = google.auth.default(scopes=[IAM_SCOPE])
  return credentials.service_account_email
