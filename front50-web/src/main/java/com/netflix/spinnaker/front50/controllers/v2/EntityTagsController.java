package com.netflix.spinnaker.front50.controllers.v2;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.exception.BadRequestException;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.tag.EntityTags;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

  @RequestMapping(method = RequestMethod.POST)
  public EntityTags create(@RequestBody final EntityTags tag) {
    return taggedEntityDAO
        .map(it -> it.create(tag.getId(), tag))
        .orElseThrow(() -> new BadRequestException("Tagging is not supported"));
  }

  @RequestMapping(value = "/batchUpdate", method = RequestMethod.POST)
  public Collection<EntityTags> batchUpdate(@RequestBody final Collection<EntityTags> tags) {
    return taggedEntityDAO
        .map(
            it -> {
              it.bulkImport(tags);
              return tags;
            })
        .orElseThrow(() -> new BadRequestException("Tagging is not supported"));
  }

  @RequestMapping(value = "/batchDelete", method = RequestMethod.POST)
  public void batchDelete(@RequestBody final Collection<String> ids) {
    if (!taggedEntityDAO.isPresent()) {
      throw new BadRequestException("Tagging is not supported");
    }
    taggedEntityDAO.get().bulkDelete(ids);
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/**")
  public void delete(HttpServletRequest request, HttpServletResponse response) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String tagId = new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());
    if (!taggedEntityDAO.isPresent()) {
      throw new BadRequestException("Tagging is not supported");
    }
    taggedEntityDAO.get().delete(tagId);
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

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
