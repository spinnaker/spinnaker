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
import google.oauth2.credentials
import google.oauth2.service_account
import requests


from google.auth.transport.requests import Request


OAUTH_TOKEN_URI = 'https://www.googleapis.com/oauth2/v4/token'


def generate_auth_token(service_account_file, client_id):
  """Generates an auth token to make requests to an IAP-protected endpoint.

  Args:
    service_account_file: [string] The path to a credentials file for a service
       account that can be used to generate the token.
    client_id: The client ID used by IAP to generate the token.
  """
  bootstrap_credentials = google.oauth2.service_account.Credentials.from_service_account_file(service_account_file)
  if isinstance(bootstrap_credentials,
                google.oauth2.credentials.Credentials):
    raise Exception('iap_generate_auth_token is only supported for service '
                    'accounts.')
  elif isinstance(bootstrap_credentials,
                  google.auth.app_engine.Credentials):
    requests_toolbelt.adapters.appengine.monkeypatch()

  signer_email = bootstrap_credentials.service_account_email
  if isinstance(bootstrap_credentials,
                google.auth.compute_engine.credentials.Credentials):
    signer = google.auth.iam.Signer(
        Request(), bootstrap_credentials, signer_email)
  else:
    signer = bootstrap_credentials.signer

  service_account_credentials = google.oauth2.service_account.Credentials(
      signer, signer_email, token_uri=OAUTH_TOKEN_URI, additional_claims={
          'target_audience': client_id
      })

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

def get_service_account_email(service_account_file):
  """Parse a service account email from a service_account_file.

  Args:
    service_account_file: [string] The path to a credentials file for a service
       account.
  """

  credentials = google.oauth2.service_account.Credentials.from_service_account_file(service_account_file)
  return credentials.service_account_email
