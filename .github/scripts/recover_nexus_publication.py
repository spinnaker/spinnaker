#!/usr/bin/env python3
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

"""Resume existing Sonatype repositories without rebuilding or re-uploading artifacts."""

import argparse
import base64
import json
import os
import random
import re
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


STAGING = "https://ossrh-staging-api.central.sonatype.com"
PORTAL = "https://central.sonatype.com/api/v1/publisher"


class RecoveryError(Exception):
    pass


class ApiError(RecoveryError):
    def __init__(self, message, status=None, retry_after=0):
        super().__init__(message)
        self.status = status
        self.retry_after = retry_after

    @property
    def retryable(self):
        return (
            self.status is None or self.status in (408, 409, 429) or self.status >= 500
        )


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the publishing token to a redirect target.
        return None


class Sonatype:
    def __init__(self, username, password):
        self.token = base64.b64encode(f"{username}:{password}".encode()).decode()
        self.opener = build_opener(NoRedirect)

    def request(self, method, url, *, json_response=True, timeout=120):
        request = Request(
            url,
            method=method,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/json",
            },
        )
        try:
            with self.opener.open(request, timeout=timeout) as response:
                body = response.read()
        except HTTPError as error:
            body = (
                error.read(8192)
                .decode(errors="replace")
                .replace(self.token, "[REDACTED]")
            )
            retry_after = (
                error.headers.get("Retry-After", "0") if error.headers else "0"
            )
            raise ApiError(
                f"{method} {url}: HTTP {error.code}: {body}",
                error.code,
                float(retry_after) if retry_after.isdigit() else 0,
            ) from error
        except (URLError, TimeoutError, ConnectionError) as error:
            raise ApiError(f"{method} {url}: {error}") from error
        if not json_response:
            return None
        try:
            result = json.loads(body)
        except (ValueError, UnicodeDecodeError) as error:
            raise RecoveryError(f"{method} {url}: invalid JSON response") from error
        if not isinstance(result, dict):
            raise RecoveryError(f"{method} {url}: expected a JSON object")
        return result


class Recovery:
    def __init__(
        self,
        api,
        version,
        checkpoint,
        *,
        timeout=1800,
        clock=time.monotonic,
        sleep=time.sleep,
        jitter=random.random,
    ):
        self.api = api
        self.version = version
        self.path = Path(checkpoint)
        self.timeout = timeout
        self.clock, self.sleep, self.jitter = clock, sleep, jitter
        self.state = {"version": version, "entries": {}}
        if self.path.exists():
            self.state = json.loads(self.path.read_text())
            if not isinstance(self.state, dict) or not isinstance(
                self.state.get("entries"), dict
            ):
                raise RecoveryError("Invalid recovery checkpoint")
            if self.state.get("version") != version:
                raise RecoveryError(
                    "Checkpoint version differs from the requested version"
                )

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.state, indent=2) + "\n")
        temporary.replace(self.path)

    def step(self, entry, request):
        deployment_id = entry.get("deployment_id")
        if not deployment_id:
            response = request(
                "GET",
                STAGING + "/manual/search/repositories?" + urlencode({"ip": "any"}),
            )
            repositories = response.get("repositories")
            if not isinstance(repositories, list) or not all(
                isinstance(repo, dict) for repo in repositories
            ):
                raise RecoveryError("Sonatype returned no repository list")
            matches = [
                repo
                for repo in repositories
                if repo.get("key") == entry["repository_id"]
            ]
            if len(matches) != 1:
                raise RecoveryError(
                    "Staging repository not found or ambiguous. Inspect the Portal and Maven Central; "
                    "resume with its Portal deployment ID if available. Missing does not mean published."
                )
            repo = matches[0]
            description = repo.get("description") or ""
            pattern = (
                r"\bio\.spinnaker\.[^:\s]+:[^:\s]+:"
                + re.escape(self.version)
                + r"(?=\s|$)"
            )
            if not re.search(pattern, description):
                raise RecoveryError(
                    f"Repository description does not match version {self.version}: {description}"
                )
            deployment_id = repo.get("portal_deployment_id")
            if deployment_id:
                validate_id(deployment_id)
                entry["deployment_id"] = deployment_id
                self.save()
            elif repo.get("state") in ("open", "closed"):
                if entry.get("upload_requested") or repo["state"] == "closed":
                    entry["status"] = (
                        "Waiting for the existing transfer's Portal deployment ID"
                    )
                    return False
                # Persist intent BEFORE the mutation: a timeout must not replay a bundle transfer.
                entry["upload_requested"] = True
                self.save()
                try:
                    request(
                        "POST",
                        STAGING
                        + "/manual/upload/repository/"
                        + quote(entry["repository_id"], safe="")
                        + "?publishing_type=portal_api",
                        json_response=False,
                    )
                except ApiError as error:
                    if error.status in (400, 401, 403, 404, 429):
                        entry["upload_requested"] = False
                        self.save()
                    raise
                entry["status"] = "Transferred; waiting for Portal deployment ID"
                return False
            else:
                raise RecoveryError(
                    "Repository has no Portal deployment ID; inspect Sonatype before retrying"
                )

        response = request(
            "POST", PORTAL + "/status?" + urlencode({"id": deployment_id})
        )
        if response.get("deploymentId") != deployment_id:
            raise RecoveryError("Portal returned an unexpected deployment ID")
        status = response.get("deploymentState")
        entry["status"] = status
        if status == "PUBLISHED":
            return True
        if status == "FAILED":
            raise RecoveryError(
                "Portal validation/publication failed: "
                + json.dumps(response.get("errors", {}))
            )
        if status == "VALIDATED":
            # Recheck status before every publish attempt, always using the same deployment ID.
            request(
                "POST",
                PORTAL + "/deployment/" + quote(deployment_id, safe=""),
                json_response=False,
            )
            entry["status"] = "Publish requested"
        elif status not in ("PENDING", "VALIDATING", "PUBLISHING"):
            raise RecoveryError(f"Unexpected Portal state: {status}")
        return False

    def run(self, repository_ids, deployment_ids):
        entries = self.state["entries"]
        targets = []
        for kind, identifiers in (
            ("repository", repository_ids),
            ("deployment", deployment_ids),
        ):
            for identifier in identifiers:
                validate_id(identifier)
                key = f"{kind}:{identifier}"
                entries.setdefault(key, {f"{kind}_id": identifier})
                targets.append(key)
        pending = list(dict.fromkeys(targets or entries.keys()))
        if not pending:
            raise RecoveryError(
                "Provide staging repository IDs, Portal deployment IDs, or a previous checkpoint"
            )
        self.save()
        deadline = self.clock() + self.timeout
        failed = []
        attempt = 0
        while pending and self.clock() < deadline:
            retry_after = 0
            for key in pending[:]:
                if self.clock() >= deadline:
                    break
                entry = entries[key]

                def request(method, url, **kwargs):
                    return self.api.request(
                        method,
                        url,
                        timeout=max(1, min(120, deadline - self.clock())),
                        **kwargs,
                    )

                try:
                    if self.step(entry, request):
                        pending.remove(key)
                except ApiError as error:
                    entry["status"] = str(error)
                    if error.retryable:
                        retry_after = max(retry_after, error.retry_after)
                    else:
                        failed.append(key)
                        pending.remove(key)
                except RecoveryError as error:
                    entry["status"] = str(error)
                    failed.append(key)
                    pending.remove(key)
                print(f"{key}: {entry['status']}", flush=True)
                self.save()
            if pending:
                base = min(120, 10 * 2 ** min(attempt, 4))
                delay = max(min(120, base * (1 + self.jitter() * 0.2)), retry_after)
                self.sleep(max(0, min(delay, deadline - self.clock())))
                attempt += 1
        for key in pending:
            entries[key]["status"] = "Timed out: " + entries[key].get(
                "status", "not checked"
            )
        self.save()
        self.summary(targets or entries.keys())
        return 1 if failed or pending else 0

    def summary(self, targets):
        lines = [
            f"### Maven publication recovery: {self.version}",
            "",
            "| Target | Portal deployment ID | Result |",
            "| --- | --- | --- |",
        ]
        for key in targets:
            entry = self.state["entries"][key]
            result = (
                str(entry.get("status", "not checked"))
                .replace("|", "/")
                .replace("\n", " ")
            )
            lines.append(f"| {key} | {entry.get('deployment_id', '')} | {result} |")
        lines += [
            "",
            "Retain the recovery checkpoint and reuse this run ID when resuming.",
            "For unresolved transfers or validation errors, inspect the Central Portal before retrying.",
        ]
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write("\n".join(lines) + "\n")


def validate_id(identifier):
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,160}", identifier):
        raise RecoveryError(f"Invalid repository/deployment ID: {identifier!r}")


def identifiers(value):
    return [item for item in re.split(r"[\s,]+", value.strip()) if item]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument(
        "--timeout",
        type=int,
        default=1800,
        help="Total deadline in seconds (default: 1800)",
    )
    args = parser.parse_args()
    try:
        if not re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", args.version) or args.timeout <= 0:
            raise RecoveryError("A valid version and positive timeout are required")
        username, password = os.environ.get("NEXUS_USERNAME"), os.environ.get(
            "NEXUS_PASSWORD"
        )
        if not username or not password:
            raise RecoveryError(
                "NEXUS_USERNAME and NEXUS_PASSWORD must contain Central publishing token credentials"
            )
        return Recovery(
            Sonatype(username, password),
            args.version,
            args.checkpoint,
            timeout=args.timeout,
        ).run(
            identifiers(os.environ.get("STAGING_REPOSITORY_IDS", "")),
            identifiers(os.environ.get("PORTAL_DEPLOYMENT_IDS", "")),
        )
    except (RecoveryError, ValueError, OSError) as error:
        print(f"Recovery failed: {error}", flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
