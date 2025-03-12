package com.netflix.spinnaker.front50.model.project;

import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collection;

public interface ProjectDAO extends ItemDAO<Project> {
  Project findByName(String name) throws NotFoundException;

  Collection<Project> all();
}
