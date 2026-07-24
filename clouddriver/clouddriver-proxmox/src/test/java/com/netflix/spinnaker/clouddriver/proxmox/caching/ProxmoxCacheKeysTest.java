/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.proxmox.caching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProxmoxCacheKeysTest {

  private static final String ACCOUNT = "myaccount";
  private static final String NODE = "pve01";

  @Test
  void vmKeyHasCorrectFormat() {
    String key = ProxmoxCacheKeys.vm(ACCOUNT, NODE, 101);
    assertThat(key).isEqualTo("proxmox;VM;myaccount;pve01;101");
  }

  @Test
  void lxcKeyHasCorrectFormat() {
    String key = ProxmoxCacheKeys.lxc(ACCOUNT, NODE, 102);
    assertThat(key).isEqualTo("proxmox;CONTAINER;myaccount;pve01;102");
  }

  @Test
  void nodeKeyHasCorrectFormat() {
    String key = ProxmoxCacheKeys.node(ACCOUNT, NODE);
    assertThat(key).isEqualTo("proxmox;NODE;myaccount;pve01");
  }

  @Test
  void storageKeyHasCorrectFormat() {
    String key = ProxmoxCacheKeys.storage(ACCOUNT, NODE, "local-lvm");
    assertThat(key).isEqualTo("proxmox;STORAGE;myaccount;pve01;local-lvm");
  }

  @Test
  void globMatchesAllKeysOfTypeForAccount() {
    String glob = ProxmoxCacheKeys.glob("VM", ACCOUNT);
    assertThat(glob).isEqualTo("proxmox;VM;myaccount;*");
  }

  @Test
  void globAllMatchesAllKeysOfTypeAcrossAccounts() {
    String glob = ProxmoxCacheKeys.globAll("VM");
    assertThat(glob).isEqualTo("proxmox;VM;*");
  }

  @Test
  void getAccountExtractsThirdSegment() {
    String key = ProxmoxCacheKeys.vm(ACCOUNT, NODE, 101);
    assertThat(ProxmoxCacheKeys.getAccount(key)).isEqualTo(ACCOUNT);
  }

  @Test
  void getNodeExtractsFourthSegment() {
    String key = ProxmoxCacheKeys.vm(ACCOUNT, NODE, 101);
    assertThat(ProxmoxCacheKeys.getNode(key)).isEqualTo(NODE);
  }

  @Test
  void getIdExtractsFifthSegment() {
    String key = ProxmoxCacheKeys.vm(ACCOUNT, NODE, 101);
    assertThat(ProxmoxCacheKeys.getId(key)).isEqualTo("101");
  }

  @Test
  void getAccountReturnsNullForShortKey() {
    assertThat(ProxmoxCacheKeys.getAccount("proxmox;VM")).isNull();
  }

  @Test
  void getNodeReturnsNullForThreeSegmentKey() {
    assertThat(ProxmoxCacheKeys.getNode("proxmox;VM;myaccount")).isNull();
  }

  @Test
  void getIdReturnsNullForFourSegmentKey() {
    String key = ProxmoxCacheKeys.node(ACCOUNT, NODE);
    assertThat(ProxmoxCacheKeys.getId(key)).isNull();
  }

  @Test
  void getAccountWorksForLxcKey() {
    String key = ProxmoxCacheKeys.lxc(ACCOUNT, NODE, 200);
    assertThat(ProxmoxCacheKeys.getAccount(key)).isEqualTo(ACCOUNT);
    assertThat(ProxmoxCacheKeys.getNode(key)).isEqualTo(NODE);
    assertThat(ProxmoxCacheKeys.getId(key)).isEqualTo("200");
  }

  @Test
  void vmIdsAreDifferentAcrossAccounts() {
    String key1 = ProxmoxCacheKeys.vm("account-a", NODE, 101);
    String key2 = ProxmoxCacheKeys.vm("account-b", NODE, 101);
    assertThat(key1).isNotEqualTo(key2);
  }
}
