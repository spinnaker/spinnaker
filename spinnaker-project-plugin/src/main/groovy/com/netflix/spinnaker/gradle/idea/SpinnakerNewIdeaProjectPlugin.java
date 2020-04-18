/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.gradle.idea;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SpinnakerNewIdeaProjectPlugin implements Plugin<Project> {

  private static final Path LICENSE_FILE = Paths.get("copyright", "ALS2.xml");
  private static final ImmutableSet<Path> COMMITTED_IDEA_FILES = ImmutableSet.of(
    Paths.get("compiler.xml"),
    LICENSE_FILE,
    Paths.get("copyright", "profiles_settings.xml"),
    Paths.get("google-java-format.xml"),
    Paths.get("gradle.xml"),
    Paths.get("vcs.xml")
  );

  private static final String COPYRIGHT_ORG_PROPERTY = "io.spinnaker.copyright";
  private static final String DEFAULT_COPYRIGHT_OWNER = "Netflix, Inc.";

  @Override
  public void apply(Project project) {
    project.afterEvaluate(unused -> {
      if (project != project.getRootProject()) {
        return;
      }

      updateGitIndex(project);
      updateCopyrightText(project);
    });
  }

  // Runs the equivalent of
  // git update-index --assume-unchanged $FILE
  // for each file in COMMITED_IDEA_FILES. After opening the project, IntelliJ will modify some of
  // these files. Doing this tells git never to consider these modifications for adding to the
  // index. If you want to commit modifications to these files you must first undo this operation.
  private static void updateGitIndex(Project project) {
    try {
      Repository repository = new FileRepositoryBuilder().setMustExist(true).setWorkTree(project.getRootDir()).build();
      DirCache index = repository.readDirCache();
      if (!needsUpdate(index)) {
        return;
      }

      if (!index.lock()) {
        throw new IOException("Couldn't get lock for git repository.");
      }
      entryStream(index).forEach(entry -> entry.setAssumeValid(true));
      index.write();
      if (!index.commit()) {
        throw new IOException("Couldn't commit changes to git repository.");
      }
    } catch (IOException e) {
      System.out.println("Error configuring git repository for idea files: " + e.getMessage());
    }
  }

  private static boolean needsUpdate(DirCache index) {
    return entryStream(index).anyMatch(entry -> !entry.isAssumeValid());
  }

  private static Stream<DirCacheEntry> entryStream(DirCache index) {
    return COMMITTED_IDEA_FILES.stream().map(path -> Paths.get(".idea").resolve(path)).map(Path::toString).map(
      index::getEntry
    ).filter(Objects::nonNull);
  }

  private static void updateCopyrightText(Project project) {

    Path licensePath = project.getRootDir().toPath().resolve(".idea").resolve(LICENSE_FILE);

    if (!licensePath.toFile().exists()) {
      return;
    }

    try {
      Document document = generateCopyrightXml(project);

      try (OutputStream out = Files.newOutputStream(licensePath)) {
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(out));
      }
    } catch (IOException | ParserConfigurationException | TransformerException e) {
      System.out.println("Error updating license: " + e.getMessage());
    }
  }

  private static Document generateCopyrightXml(Project project) throws ParserConfigurationException {

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();

    Document document = db.newDocument();

    Element component = document.createElement("component");
    component.setAttribute("name", "CopyrightManager");
    document.appendChild(component);

    Element copyright = document.createElement("copyright");
    component.appendChild(copyright);

    Element notice = document.createElement("option");
    notice.setAttribute("name", "notice");
    notice.setAttribute("value", getCopyrightText(project));
    copyright.appendChild(notice);

    Element name = document.createElement("option");
    name.setAttribute("name", "myName");
    name.setAttribute("value", "ALS2");
    copyright.appendChild(name);

    return document;
  }

  static String getCopyrightText(Project project) {
    String copyrightOrg = project.hasProperty(COPYRIGHT_ORG_PROPERTY) ? (String) project.property(
      COPYRIGHT_ORG_PROPERTY
    ) : DEFAULT_COPYRIGHT_OWNER;
    return "Copyright $today.year " + copyrightOrg + "\n\n"
      + "Licensed under the Apache License, Version 2.0 (the \"License\");\n"
      + "you may not use this file except in compliance with the License.\n"
      + "You may obtain a copy of the License at\n" + "\n" + "  http://www.apache.org/licenses/LICENSE-2.0\n" + "\n"
      + "Unless required by applicable law or agreed to in writing, software\n"
      + "distributed under the License is distributed on an \"AS IS\" BASIS,\n"
      + "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
      + "See the License for the specific language governing permissions and\n" + "limitations under the License.";
  }
}
