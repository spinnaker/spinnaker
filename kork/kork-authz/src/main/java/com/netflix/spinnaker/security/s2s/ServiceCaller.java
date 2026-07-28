/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.s2s;

/**
 * An authenticated internal service caller, normalized from whatever transport mechanism proved its
 * identity (mTLS client certificate, mesh header, or Kubernetes ServiceAccount token).
 *
 * <p>This is intentionally distinct from the acting <em>user</em> identity (the human or service
 * account carried by Gate's minted identity token): a downstream request has up to two independent
 * principals — the calling <em>service</em> (this type) and the acting <em>user</em>. Authorization
 * may consult either.
 *
 * @param service the resolved first-party service, or {@link SpinnakerService#UNKNOWN}
 * @param subject the raw identity string the transport presented (e.g. an X.509 subject DN or a
 *     {@code system:serviceaccount:ns:name} token subject), retained for logging/audit
 * @param source the resolver that produced this caller (e.g. {@code x509-subject}), for
 *     observability
 */
public record ServiceCaller(SpinnakerService service, String subject, String source) {

  public ServiceCaller {
    if (service == null) {
      service = SpinnakerService.UNKNOWN;
    }
  }

  /** Whether this caller resolved to a recognized first-party Spinnaker service. */
  public boolean isKnown() {
    return service.isKnown();
  }
}
