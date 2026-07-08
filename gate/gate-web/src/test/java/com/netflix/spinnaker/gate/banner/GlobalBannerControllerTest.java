/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.banner;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ExtendWith(MockitoExtension.class)
class GlobalBannerControllerTest {

  @Mock GlobalBannerService service;

  GlobalBannerProperties properties;
  GlobalBannerController controller;
  MockMvc mockMvc;
  ObjectMapper objectMapper = new ObjectMapper();

  @ControllerAdvice
  static class AccessDeniedTo403Advice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handle() {}
  }

  @BeforeEach
  void setUp() {
    properties = new GlobalBannerProperties();
    controller = new GlobalBannerController(service, properties);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new AccessDeniedTo403Advice())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private BannerRecord banner(String id, String message, boolean enabled) {
    BannerRecord b = new BannerRecord();
    b.setId(id);
    b.setMessage(message);
    b.setEnabled(enabled);
    return b;
  }

  private void setCurrentUser(String username) {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(username, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  // ---------------------------------------------------------------------------
  // GET /banners — public, no auth required
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /banners")
  class GetActiveBanners {

    @Test
    @DisplayName("returns 200 and active banner list without authentication")
    void returnsActiveBanners() throws Exception {
      when(service.getActiveBanners()).thenReturn(List.of(banner("b1", "Hello", true)));

      mockMvc
          .perform(get("/banners"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("b1"))
          .andExpect(jsonPath("$[0].message").value("Hello"));
    }

    @Test
    @DisplayName("returns 200 with empty list when no active banners")
    void returnsEmptyList() throws Exception {
      when(service.getActiveBanners()).thenReturn(List.of());

      mockMvc.perform(get("/banners")).andExpect(status().isOk()).andExpect(content().string("[]"));
    }
  }

  // ---------------------------------------------------------------------------
  // GET /banners/all — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /banners/all")
  class GetAllBanners {

    @Test
    @DisplayName("returns all banners including disabled")
    void returnsAll() throws Exception {
      when(service.getAllBanners())
          .thenReturn(List.of(banner("b1", "Active", true), banner("b2", "Disabled", false)));

      mockMvc
          .perform(get("/banners/all"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2));
    }
  }

  // ---------------------------------------------------------------------------
  // GET /banners/{id} — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /banners/{id}")
  class GetBannerById {

    @Test
    @DisplayName("returns 200 when banner exists")
    void returns200WhenFound() throws Exception {
      when(service.getBannerById("b1")).thenReturn(Optional.of(banner("b1", "Found", true)));

      mockMvc
          .perform(get("/banners/b1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("b1"));
    }

    @Test
    @DisplayName("returns 404 when banner does not exist")
    void returns404WhenNotFound() throws Exception {
      when(service.getBannerById("ghost")).thenReturn(Optional.empty());

      mockMvc.perform(get("/banners/ghost")).andExpect(status().isNotFound());
    }
  }

  // ---------------------------------------------------------------------------
  // PUT /banners — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("PUT /banners")
  class SaveBanner {

    @Test
    @DisplayName("returns 200 and saved banner for valid request")
    void savesValidBanner() throws Exception {
      BannerRecord input = banner("b1", "New banner", true);
      when(service.save(any())).thenReturn(input);

      mockMvc
          .perform(
              put("/banners")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("b1"))
          .andExpect(jsonPath("$.message").value("New banner"));
    }

    @Test
    @DisplayName("returns 400 when id is missing")
    void returns400WhenIdMissing() throws Exception {
      BannerRecord input = new BannerRecord();
      input.setMessage("no id");

      mockMvc
          .perform(
              put("/banners")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(service);
    }

    @Test
    @DisplayName("returns 400 when message is missing")
    void returns400WhenMessageMissing() throws Exception {
      BannerRecord input = new BannerRecord();
      input.setId("b1");

      mockMvc
          .perform(
              put("/banners")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(service);
    }

    @Test
    @DisplayName("returns 400 when message exceeds maxMessageLength")
    void returns400WhenMessageTooLong() throws Exception {
      BannerRecord input = banner("b1", "x".repeat(properties.getMaxMessageLength() + 1), true);

      mockMvc
          .perform(
              put("/banners")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(service);
    }
  }

  // ---------------------------------------------------------------------------
  // POST /banners/{id} — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("POST /banners/{id}")
  class UpdateBanner {

    @Test
    @DisplayName("returns 200 and updated banner when it exists")
    void updatesExisting() throws Exception {
      BannerRecord existing = banner("b1", "Old", true);
      existing.setCreatedAt("2026-01-01T00:00:00Z");
      BannerRecord updated = banner("b1", "Updated", true);

      when(service.getBannerById("b1")).thenReturn(Optional.of(existing));
      when(service.save(any())).thenReturn(updated);

      mockMvc
          .perform(
              post("/banners/b1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(updated)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("Updated"));
    }

    @Test
    @DisplayName("returns 404 when banner does not exist")
    void returns404WhenNotFound() throws Exception {
      when(service.getBannerById("ghost")).thenReturn(Optional.empty());

      mockMvc
          .perform(
              post("/banners/ghost")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(banner("ghost", "msg", true))))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("path variable id overrides any id in the request body")
    void pathIdWins() throws Exception {
      BannerRecord existing = banner("path-id", "Existing", true);
      existing.setCreatedAt("2026-01-01T00:00:00Z");
      when(service.getBannerById("path-id")).thenReturn(Optional.of(existing));

      BannerRecord body = banner("body-id", "Updated", true);
      when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockMvc
          .perform(
              post("/banners/path-id")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("path-id"));
    }

    @Test
    @DisplayName("preserves createdAt from existing record")
    void preservesCreatedAt() throws Exception {
      BannerRecord existing = banner("b1", "Old", true);
      existing.setCreatedAt("2026-01-01T00:00:00Z");
      when(service.getBannerById("b1")).thenReturn(Optional.of(existing));
      when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

      BannerRecord body = banner("b1", "New msg", true);

      mockMvc
          .perform(
              post("/banners/b1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));
    }
  }

  // ---------------------------------------------------------------------------
  // DELETE /banners/{id} — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DELETE /banners/{id}")
  class DeleteBanner {

    @Test
    @DisplayName("returns 204 when banner is deleted")
    void returns204WhenDeleted() throws Exception {
      when(service.delete("b1")).thenReturn(true);

      mockMvc.perform(delete("/banners/b1")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("returns 404 when banner does not exist")
    void returns404WhenNotFound() throws Exception {
      when(service.delete("ghost")).thenReturn(false);

      mockMvc.perform(delete("/banners/ghost")).andExpect(status().isNotFound());
    }
  }

  // ---------------------------------------------------------------------------
  // DELETE /banners — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DELETE /banners")
  class DeleteAllBanners {

    @Test
    @DisplayName("returns 204 with X-Deleted-Count header")
    void returns204WithCount() throws Exception {
      when(service.deleteAll()).thenReturn(3);

      mockMvc
          .perform(delete("/banners"))
          .andExpect(status().isNoContent())
          .andExpect(header().string("X-Deleted-Count", "3"));
    }
  }

  // ---------------------------------------------------------------------------
  // POST /banners/refresh — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("POST /banners/refresh")
  class ForceRefresh {

    @Test
    @DisplayName("returns 200 and triggers cache refresh")
    void returns200AndRefreshes() throws Exception {
      mockMvc
          .perform(post("/banners/refresh"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ok"));

      verify(service).forceRefresh();
    }
  }
}
