package com.netflix.spinnaker.front50.controllers.v2;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.exception.BadRequestException;
import com.netflix.spinnaker.front50.model.tag.EntityTags;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

@RestController
@RequestMapping(value = "/v2/tags", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityTagsController {

  private static final Logger log = LoggerFactory.getLogger(EntityTagsController.class);

  private final Optional<EntityTagsDAO> taggedEntityDAO;

  public EntityTagsController(Optional<EntityTagsDAO> taggedEntityDAO) {
    this.taggedEntityDAO = taggedEntityDAO;
  }

  @RequestMapping(method = RequestMethod.GET)
  public Set<EntityTags> tags(
      @RequestParam(value = "prefix", required = false) final String prefix,
      @RequestParam(value = "ids", required = false) Collection<String> ids,
      @RequestParam(value = "refresh", required = false) Boolean refresh) {

    Collection<String> tagIds = Optional.ofNullable(ids).orElseGet(ArrayList::new);
    if (prefix == null && tagIds.isEmpty()) {
      throw new BadRequestException("Either 'prefix' or 'ids' parameter is required");
    }

    if (!tagIds.isEmpty()) {
      return findAllByIds(tagIds);
    }

    boolean refreshFlag = (refresh == null) ? true : refresh;

    return taggedEntityDAO
        .map(
            dao ->
                dao.all(refreshFlag).stream()
                    .filter(
                        it -> {
                          if (Strings.isNullOrEmpty(prefix)) {
                            return true;
                          } else {
                            return it.getId().startsWith(prefix);
                          }
                        })
                    .collect(Collectors.toSet()))
        .orElse(null);
  }

  @RequestMapping(value = "/**", method = RequestMethod.GET)
  public EntityTags tag(HttpServletRequest request) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    final String searchTerm =
        new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());

    return taggedEntityDAO
        .map(it -> it.findById(searchTerm))
        .orElseThrow(() -> new NotFoundException(format("No tags found for '%s'", searchTerm)));
  }

  // Write endpoints (POST/DELETE) have been removed. Entity tag storage moved to
  // clouddriver's SQL provider; these read endpoints remain only to feed the one-shot
  // Front50→clouddriver migrator and can be removed in a follow-up.

  private Set<EntityTags> findAllByIds(Collection<String> ids) {
    return taggedEntityDAO
        .map(
            dao ->
                ids.stream()
                    .map(
                        it -> {
                          try {
                            return dao.findById(it);
                          } catch (Exception e) {
                            // ignored
                            return null;
                          }
                        })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()))
        .orElseGet(HashSet::new);
  }
}
