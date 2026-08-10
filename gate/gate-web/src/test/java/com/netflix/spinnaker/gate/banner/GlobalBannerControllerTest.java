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
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.security.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GlobalBannerControllerTest {

  @Mock GlobalBannerService service;
  @Mock PermissionService permissionService;

  GlobalBannerProperties properties;
  GlobalBannerController controller;
  MockMvc mockMvc;
  ObjectMapper objectMapper = new ObjectMapper();

  @ControllerAdvice
  static class ExceptionAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handleAccessDenied() {}

    @ExceptionHandler(ResponseStatusException.class)
    void handleResponseStatus(
        ResponseStatusException ex, jakarta.servlet.http.HttpServletResponse res)
        throws java.io.IOException {
      res.sendError(ex.getStatusCode().value(), ex.getReason());
    }
  }

  @BeforeEach
  void setUp() {
    properties = new GlobalBannerProperties();
    controller = new GlobalBannerController(service, properties, permissionService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ExceptionAdvice())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
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

  /** Sets a principal in the security context so @SpinnakerUser resolves a non-null User. */
  private void authenticateAs(String username) {
    User principal = new User();
    principal.setEmail(username);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  private void asAdmin(String username) {
    authenticateAs(username);
    Mockito.lenient().when(permissionService.isAdmin(username)).thenReturn(true);
  }

  private void asNonAdmin(String username) {
    authenticateAs(username);
    Mockito.lenient().when(permissionService.isAdmin(username)).thenReturn(false);
  }

  // ---------------------------------------------------------------------------
  // GET /banners — any authenticated user
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /banners")
  class GetActiveBanners {

    @Test
    @DisplayName("returns 200 and active banner list")
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
    @DisplayName("admin gets all banners including disabled")
    void adminGetsAll() throws Exception {
      asAdmin("admin-user");
      when(service.getAllBanners())
          .thenReturn(List.of(banner("b1", "Active", true), banner("b2", "Disabled", false)));

      mockMvc
          .perform(get("/banners/all"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc.perform(get("/banners/all")).andExpect(status().isForbidden());
      verifyNoInteractions(service);
    }
  }

  // ---------------------------------------------------------------------------
  // GET /banners/{id} — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("GET /banners/{id}")
  class GetBannerById {

    @Test
    @DisplayName("admin gets 200 when banner exists")
    void adminGets200WhenFound() throws Exception {
      asAdmin("admin-user");
      when(service.getBannerById("b1")).thenReturn(Optional.of(banner("b1", "Found", true)));

      mockMvc
          .perform(get("/banners/b1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("b1"));
    }

    @Test
    @DisplayName("admin gets 404 when banner does not exist")
    void adminGets404WhenNotFound() throws Exception {
      asAdmin("admin-user");
      when(service.getBannerById("ghost")).thenReturn(Optional.empty());

      mockMvc.perform(get("/banners/ghost")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc.perform(get("/banners/b1")).andExpect(status().isForbidden());
      verifyNoInteractions(service);
    }
  }

  // ---------------------------------------------------------------------------
  // PUT /banners — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("PUT /banners")
  class SaveBanner {

    @Test
    @DisplayName("admin saves valid banner")
    void adminSavesValidBanner() throws Exception {
      asAdmin("admin-user");
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
      asAdmin("admin-user");
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
      asAdmin("admin-user");
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
      asAdmin("admin-user");
      BannerRecord input = banner("b1", "x".repeat(properties.getMaxMessageLength() + 1), true);

      mockMvc
          .perform(
              put("/banners")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input)))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(service);
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc
          .perform(
              put("/banners")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(banner("b1", "msg", true))))
          .andExpect(status().isForbidden());
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
    @DisplayName("admin updates existing banner")
    void adminUpdatesExisting() throws Exception {
      asAdmin("admin-user");
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
      asAdmin("admin-user");
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
      asAdmin("admin-user");
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
      asAdmin("admin-user");
      BannerRecord existing = banner("b1", "Old", true);
      existing.setCreatedAt("2026-01-01T00:00:00Z");
      when(service.getBannerById("b1")).thenReturn(Optional.of(existing));
      when(service.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockMvc
          .perform(
              post("/banners/b1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(banner("b1", "New msg", true))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc
          .perform(
              post("/banners/b1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(banner("b1", "msg", true))))
          .andExpect(status().isForbidden());
      verifyNoInteractions(service);
    }
  }

  // ---------------------------------------------------------------------------
  // DELETE /banners/{id} — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DELETE /banners/{id}")
  class DeleteBanner {

    @Test
    @DisplayName("admin gets 204 when banner is deleted")
    void adminGets204WhenDeleted() throws Exception {
      asAdmin("admin-user");
      when(service.delete("b1")).thenReturn(true);

      mockMvc.perform(delete("/banners/b1")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("admin gets 404 when banner does not exist")
    void adminGets404WhenNotFound() throws Exception {
      asAdmin("admin-user");
      when(service.delete("ghost")).thenReturn(false);

      mockMvc.perform(delete("/banners/ghost")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc.perform(delete("/banners/b1")).andExpect(status().isForbidden());
      verifyNoInteractions(service);
    }
  }

  // ---------------------------------------------------------------------------
  // DELETE /banners — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("DELETE /banners")
  class DeleteAllBanners {

    @Test
    @DisplayName("admin gets 204 with X-Deleted-Count header")
    void adminGets204WithCount() throws Exception {
      asAdmin("admin-user");
      when(service.deleteAll()).thenReturn(3);

      mockMvc
          .perform(delete("/banners"))
          .andExpect(status().isNoContent())
          .andExpect(header().string("X-Deleted-Count", "3"));
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc.perform(delete("/banners")).andExpect(status().isForbidden());
      verifyNoInteractions(service);
    }
  }

  // ---------------------------------------------------------------------------
  // POST /banners/refresh — admin only
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("POST /banners/refresh")
  class ForceRefresh {

    @Test
    @DisplayName("admin gets 200 and triggers cache refresh")
    void adminGets200AndRefreshes() throws Exception {
      asAdmin("admin-user");

      mockMvc
          .perform(post("/banners/refresh"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ok"));

      verify(service).forceRefresh();
    }

    @Test
    @DisplayName("non-admin gets 403")
    void nonAdminForbidden() throws Exception {
      asNonAdmin("regular-user");

      mockMvc.perform(post("/banners/refresh")).andExpect(status().isForbidden());
      verifyNoInteractions(service);
    }
  }
}
