# Copyright 2026 spinnaker.io
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

import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock
from urllib.error import HTTPError

from recover_nexus_publication import (
    ApiError,
    NoRedirect,
    Recovery,
    RecoveryError,
    Sonatype,
)


VERSION = "2026.3.2"
SEARCH = "/manual/search/repositories?"


def repository(state="open", deployment=None, version=VERSION):
    return {
        "repositories": [
            {
                "key": "kork-repo",
                "state": state,
                "description": f"io.spinnaker.kork:kork:{version}",
                "portal_deployment_id": deployment,
            }
        ]
    }


def status(state, deployment="portal-kork", **kwargs):
    return {"deploymentId": deployment, "deploymentState": state, **kwargs}


class Clock:
    def __init__(self):
        self.now = 0
        self.delays = []

    def sleep(self, delay):
        self.delays.append(delay)
        self.now += delay


class Api:
    def __init__(self, events):
        self.events = list(events)
        self.calls = []

    def request(self, method, url, **kwargs):
        self.calls.append((method, url))
        if not self.events:
            raise AssertionError(f"Unexpected request: {method} {url}")
        expected_method, path, result = self.events.pop(0)
        if method != expected_method or path not in url:
            raise AssertionError(
                f"Expected {expected_method} {path}, got {method} {url}"
            )
        if isinstance(result, Exception):
            raise result
        if callable(result):
            return result()
        return result


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.checkpoint = Path(self.directory.name) / "checkpoint.json"
        self.clock = Clock()

    def recover(self, events, repositories=(), deployments=(), timeout=1800):
        api = Api(events)
        recovery = Recovery(
            api,
            VERSION,
            self.checkpoint,
            timeout=timeout,
            clock=lambda: self.clock.now,
            sleep=self.clock.sleep,
            jitter=lambda: 0,
        )
        result = recovery.run(repositories, deployments)
        self.assertEqual([], api.events, "Some expected requests were not executed")
        return result, api

    def test_transfers_open_repository_asynchronously_and_waits_until_published(self):
        result, api = self.recover(
            [
                ("GET", SEARCH, repository()),
                (
                    "POST",
                    "/manual/upload/repository/kork-repo?publishing_type=portal_api",
                    None,
                ),
                ("GET", SEARCH, repository("closed", "portal-kork")),
                ("POST", "/status?id=portal-kork", status("PENDING")),
                ("POST", "/status?id=portal-kork", status("VALIDATING")),
                ("POST", "/status?id=portal-kork", status("VALIDATED")),
                ("POST", "/deployment/portal-kork", None),
                ("POST", "/status?id=portal-kork", status("PUBLISHING")),
                ("POST", "/status?id=portal-kork", status("PUBLISHED")),
            ],
            repositories=["kork-repo"],
        )
        self.assertEqual(0, result)
        self.assertEqual(
            "PUBLISHED",
            json.loads(self.checkpoint.read_text())["entries"]["repository:kork-repo"][
                "status"
            ],
        )
        self.assertEqual(1, sum("/manual/upload/" in url for _, url in api.calls))

    def test_resumes_partial_release_and_skips_published_deployment(self):
        result, api = self.recover(
            [
                (
                    "POST",
                    "/status?id=already-published",
                    status("PUBLISHED", "already-published"),
                ),
                ("POST", "/status?id=portal-kork", status("VALIDATED")),
                ("POST", "/deployment/portal-kork", None),
                ("POST", "/status?id=portal-kork", status("PUBLISHED")),
            ],
            deployments=["already-published", "portal-kork"],
        )
        self.assertEqual(0, result)
        self.assertFalse(
            any("/deployment/already-published" in url for _, url in api.calls)
        )

    def test_524_during_transfer_reuses_discovered_deployment_without_replaying_transfer(
        self,
    ):
        result, api = self.recover(
            [
                ("GET", SEARCH, repository()),
                ("POST", "/manual/upload/", ApiError("HTTP 524", 524)),
                ("GET", SEARCH, repository("closed", "portal-kork")),
                ("POST", "/status?id=portal-kork", status("PUBLISHED")),
            ],
            repositories=["kork-repo"],
        )
        self.assertEqual(0, result)
        self.assertEqual(1, sum("/manual/upload/" in url for _, url in api.calls))

    def test_transfer_intent_is_persisted_before_the_network_request(self):
        def transfer():
            saved = json.loads(self.checkpoint.read_text())
            self.assertTrue(
                saved["entries"]["repository:kork-repo"]["upload_requested"]
            )
            raise ApiError("Connection lost", None)

        result, _ = self.recover(
            [
                ("GET", SEARCH, repository()),
                ("POST", "/manual/upload/", transfer),
                ("GET", SEARCH, repository("closed", "portal-kork")),
                ("POST", "/status?", status("PUBLISHED")),
            ],
            repositories=["kork-repo"],
        )
        self.assertEqual(0, result)

    def test_uncertain_transfer_times_out_and_checkpoint_prevents_reupload_on_resume(
        self,
    ):
        result, _ = self.recover(
            [
                ("GET", SEARCH, repository()),
                ("POST", "/manual/upload/", ApiError("HTTP 524", 524)),
                ("GET", SEARCH, repository()),
            ],
            repositories=["kork-repo"],
            timeout=25,
        )
        self.assertEqual(1, result)
        self.assertTrue(
            json.loads(self.checkpoint.read_text())["entries"]["repository:kork-repo"][
                "upload_requested"
            ]
        )
        result, api = self.recover(
            [
                ("GET", SEARCH, repository("closed", "portal-kork")),
                ("POST", "/status?id=portal-kork", status("PUBLISHED")),
            ]
        )
        self.assertEqual(0, result)
        self.assertFalse(any("/manual/upload/" in url for _, url in api.calls))

    def test_524_during_publish_checks_status_before_retry(self):
        result, api = self.recover(
            [
                ("POST", "/status?id=portal-kork", status("VALIDATED")),
                ("POST", "/deployment/portal-kork", ApiError("HTTP 524", 524)),
                ("POST", "/status?id=portal-kork", status("PUBLISHING")),
                ("POST", "/status?id=portal-kork", status("PUBLISHED")),
            ],
            deployments=["portal-kork"],
        )
        self.assertEqual(0, result)
        self.assertEqual(1, sum("/deployment/" in url for _, url in api.calls))

    def test_transient_status_errors_use_exponential_backoff_and_retry_after(self):
        result, _ = self.recover(
            [
                ("POST", "/status?", ApiError("HTTP 524", 524)),
                ("POST", "/status?", ApiError("HTTP 429", 429, retry_after=90)),
                ("POST", "/status?", ApiError("HTTP 503", 503)),
                ("POST", "/status?", status("PUBLISHED")),
            ],
            deployments=["portal-kork"],
        )
        self.assertEqual(0, result)
        self.assertEqual([10, 90, 40], self.clock.delays)

    def test_rate_limited_transfer_can_retry_after_server_rejected_it(self):
        result, api = self.recover(
            [
                ("GET", SEARCH, repository()),
                ("POST", "/manual/upload/", ApiError("HTTP 429", 429, retry_after=30)),
                ("GET", SEARCH, repository()),
                ("POST", "/manual/upload/", None),
                ("GET", SEARCH, repository("closed", "portal-kork")),
                ("POST", "/status?", status("PUBLISHED")),
            ],
            repositories=["kork-repo"],
        )
        self.assertEqual(0, result)
        self.assertEqual(30, self.clock.delays[0])
        self.assertEqual(2, sum("/manual/upload/" in url for _, url in api.calls))

    def test_validation_failure_stays_failed_while_other_deployment_finishes(self):
        result, _ = self.recover(
            [
                (
                    "POST",
                    "/status?id=portal-kork",
                    status("FAILED", errors={"pom": ["Invalid signature"]}),
                ),
                ("POST", "/status?id=other", status("PUBLISHED", "other")),
            ],
            deployments=["portal-kork", "other"],
        )
        self.assertEqual(1, result)
        self.assertIn("Invalid signature", self.checkpoint.read_text())
        self.assertEqual([], self.clock.delays)

    def test_authentication_failure_is_not_retried(self):
        result, _ = self.recover(
            [
                ("POST", "/status?", ApiError("HTTP 401", 401)),
            ],
            deployments=["portal-kork"],
        )
        self.assertEqual(1, result)
        self.assertEqual([], self.clock.delays)

    def test_missing_repository_is_not_treated_as_published(self):
        result, _ = self.recover(
            [
                ("GET", SEARCH, {"repositories": []}),
            ],
            repositories=["kork-repo"],
        )
        self.assertEqual(1, result)
        self.assertIn("Missing does not mean published", self.checkpoint.read_text())

    def test_wrong_version_prevents_publication(self):
        result, api = self.recover(
            [
                ("GET", SEARCH, repository(version="2026.3.1")),
            ],
            repositories=["kork-repo"],
        )
        self.assertEqual(1, result)
        self.assertEqual(1, len(api.calls))

    def test_closed_repository_without_deployment_id_is_not_retransferred(self):
        result, api = self.recover(
            [
                ("GET", SEARCH, repository("closed")),
                ("GET", SEARCH, repository("closed")),
            ],
            repositories=["kork-repo"],
            timeout=25,
        )
        self.assertEqual(1, result)
        self.assertFalse(any(method == "POST" for method, _ in api.calls))

    def test_publishing_deadline_remains_a_failure(self):
        result, _ = self.recover(
            [
                ("POST", "/status?", status("PUBLISHING")),
                ("POST", "/status?", status("PUBLISHING")),
            ],
            deployments=["portal-kork"],
            timeout=25,
        )
        self.assertEqual(1, result)
        self.assertIn("Timed out", self.checkpoint.read_text())

    def test_polling_backoff_is_capped(self):
        result, _ = self.recover(
            [
                *[("POST", "/status?", status("PENDING")) for _ in range(7)],
                ("POST", "/status?", status("PUBLISHED")),
            ],
            deployments=["portal-kork"],
        )
        self.assertEqual(0, result)
        self.assertEqual([10, 20, 40, 80, 120, 120, 120], self.clock.delays)

    def test_unknown_portal_state_stops_without_publishing(self):
        result, api = self.recover(
            [
                ("POST", "/status?", status("UNKNOWN")),
            ],
            deployments=["portal-kork"],
        )
        self.assertEqual(1, result)
        self.assertEqual(1, len(api.calls))

    def test_wrong_portal_deployment_id_stops_without_publishing(self):
        result, api = self.recover(
            [
                ("POST", "/status?", status("VALIDATED", "different-deployment")),
            ],
            deployments=["portal-kork"],
        )
        self.assertEqual(1, result)
        self.assertEqual(1, len(api.calls))

    def test_invalid_id_is_rejected_before_making_requests(self):
        with self.assertRaisesRegex(RecoveryError, "Invalid repository/deployment ID"):
            self.recover([], deployments=["../../other-deployment"])

    def test_checkpoint_with_wrong_version_is_rejected(self):
        self.checkpoint.write_text(json.dumps({"version": "2026.3.1", "entries": {}}))
        with self.assertRaisesRegex(RecoveryError, "Checkpoint version"):
            self.recover([])

    def test_empty_selection_is_rejected(self):
        with self.assertRaisesRegex(RecoveryError, "Provide staging"):
            self.recover([])


class ClientTest(unittest.TestCase):
    def test_uses_bearer_token_and_decodes_status_response(self):
        api = Sonatype("token-user", "token-password")
        api.opener = Mock()
        api.opener.open.return_value.__enter__ = Mock(
            return_value=Mock(read=lambda: b'{"deploymentState":"PUBLISHED"}')
        )
        api.opener.open.return_value.__exit__ = Mock(return_value=False)
        self.assertEqual(
            {"deploymentState": "PUBLISHED"},
            api.request(
                "POST",
                "https://central.sonatype.com/api/v1/publisher/status?id=example",
            ),
        )
        request = api.opener.open.call_args.args[0]
        self.assertEqual("POST", request.method)
        self.assertEqual("Bearer " + api.token, request.get_header("Authorization"))

    def test_http_error_redacts_token_and_preserves_retry_after(self):
        api = Sonatype("token-user", "token-password")
        api.opener = Mock()
        api.opener.open.side_effect = HTTPError(
            "https://central.sonatype.com",
            429,
            "Rate limited",
            {"Retry-After": "60"},
            io.BytesIO(api.token.encode()),
        )
        with self.assertRaises(ApiError) as raised:
            api.request(
                "POST",
                "https://central.sonatype.com/api/v1/publisher/status?id=example",
            )
        self.assertNotIn(api.token, str(raised.exception))
        self.assertEqual(60, raised.exception.retry_after)
        self.assertTrue(raised.exception.retryable)

    def test_redirects_are_not_followed(self):
        self.assertIsNone(
            NoRedirect().redirect_request(
                None, None, 302, "Redirect", {}, "https://example.com"
            )
        )


if __name__ == "__main__":
    unittest.main()
