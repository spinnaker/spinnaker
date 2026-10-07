/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.banner.BannerRecord;
import com.netflix.spinnaker.gate.banner.GlobalBannerProperties;
import com.netflix.spinnaker.gate.banner.GlobalBannerService;
import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalBannerToolsTest {

  @Mock private GlobalBannerService globalBannerService;

  private GlobalBannerTools globalBannerTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    GlobalBannerProperties bannerProperties = new GlobalBannerProperties();
    globalBannerTools =
        new GlobalBannerTools(
            globalBannerService, bannerProperties, new McpAccessGuard(properties));
  }

  @Test
  void listGlobalBannersReturnsAllBanners() {
    BannerRecord banner = new BannerRecord();
    banner.setId("b1");
    when(globalBannerService.getAllBanners()).thenReturn(List.of(banner));

    List<BannerRecord> result = globalBannerTools.listGlobalBanners();

    assertThat(result).containsExactly(banner);
  }

  @Test
  void setGlobalBannerSavesNewBannerWithoutCreatedAt() {
    when(globalBannerService.getBannerById("b1")).thenReturn(Optional.empty());
    when(globalBannerService.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    globalBannerTools.setGlobalBanner("b1", "hello", true, "#333", "#fff3cd", "14px", null, null);

    ArgumentCaptor<BannerRecord> captor = ArgumentCaptor.forClass(BannerRecord.class);
    verify(globalBannerService).save(captor.capture());
    BannerRecord saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo("b1");
    assertThat(saved.getMessage()).isEqualTo("hello");
    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.getCreatedAt()).isNull();
  }

  @Test
  void setGlobalBannerPreservesCreatedAtWhenUpdatingExisting() {
    BannerRecord existing = new BannerRecord();
    existing.setId("b1");
    existing.setCreatedAt("2020-01-01T00:00:00Z");
    when(globalBannerService.getBannerById("b1")).thenReturn(Optional.of(existing));
    when(globalBannerService.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    globalBannerTools.setGlobalBanner("b1", "updated", true, null, null, null, null, null);

    ArgumentCaptor<BannerRecord> captor = ArgumentCaptor.forClass(BannerRecord.class);
    verify(globalBannerService).save(captor.capture());
    assertThat(captor.getValue().getCreatedAt()).isEqualTo("2020-01-01T00:00:00Z");
  }

  @Test
  void setGlobalBannerRejectsBlankMessage() {
    assertThatThrownBy(
            () -> globalBannerTools.setGlobalBanner("b1", "  ", true, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setGlobalBannerRejectsMessageOverMaxLength() {
    String tooLong = "x".repeat(2001);
    assertThatThrownBy(
            () ->
                globalBannerTools.setGlobalBanner(
                    "b1", tooLong, true, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setGlobalBannerRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(
            () ->
                globalBannerTools.setGlobalBanner(
                    "b1", "hello", true, null, null, null, null, null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void deleteGlobalBannerDeletesById() {
    when(globalBannerService.delete("b1")).thenReturn(true);

    globalBannerTools.deleteGlobalBanner("b1");

    verify(globalBannerService).delete("b1");
  }

  @Test
  void deleteGlobalBannerThrowsWhenNotFound() {
    when(globalBannerService.delete("missing")).thenReturn(false);

    assertThatThrownBy(() -> globalBannerTools.deleteGlobalBanner("missing"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deleteGlobalBannerRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(() -> globalBannerTools.deleteGlobalBanner("b1"))
        .isInstanceOf(McpReadOnlyModeException.class);
  }
}
