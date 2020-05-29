package com.netflix.spinnaker.front50.model.project;

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.ItemDAO;
import java.util.Collection;

public interface ProjectDAO extends ItemDAO<Project> {
  Project findByName(String name) throws NotFoundException;

  Collection<Project> all();
}
