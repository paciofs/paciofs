/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

// prints the number of issues reported by checkstyle and spotbugs
public class CheckSummarizer {
  public static void main(String[] args) throws Exception {
    File dir = new File(".");
    boolean skipCheckstyle = false;
    boolean skipSpotbugs = false;

    for (int i = 0; i < args.length; ++i) {
      switch (args[i]) {
        case "--dir":
          dir = new File(args[++i]);
          break;
        case "--skip-checkstyle":
          skipCheckstyle = Boolean.parseBoolean(args[++i]);
          break;
        case "--skip-spotbugs":
          skipSpotbugs = Boolean.parseBoolean(args[++i]);
          break;
        default:
          throw new IllegalArgumentException(args[i]);
      }
    }

    if (!skipCheckstyle) {
      countXmlTags(new File(dir, "checkstyle-result.xml"), "error", "checkstyle violation",
          "file://" + new File(dir, "site/checkstyle.html").getPath() + "",
          (node) -> node.getParentNode().getAttributes().getNamedItem("name").getNodeValue());
    }

    if (!skipSpotbugs) {
      countXmlTags(
          new File(dir, "spotbugsXml.xml"), "BugInstance", "bug", "mvn spotbugs:gui", (node) -> {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); ++i) {
              Node child = children.item(i);
              Node sourcePathAttribute = child.getAttributes().getNamedItem("sourcepath");
              if (sourcePathAttribute != null) {
                return sourcePathAttribute.getNodeValue();
              }
            }

            return null;
          });
    }
  }

  private static void countXmlTags(File xml, String tagName, String message, String see,
      Function<Node, String> filenameExtractor)
      throws ParserConfigurationException, IOException, SAXException, GitAPIException {
    final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    final Document document = builder.parse(xml);
    final NodeList tags = document.getElementsByTagName(tagName);

    int tagCount = tags.getLength();
    System.out.print("[");
    if (tagCount > 0) {
      // bold yellow
      System.out.print((char) 27 + "[1;33mWARNING" + (char) 27 + "[0m");
    } else {
      // bold blue
      System.out.print((char) 27 + "[1;34mINFO" + (char) 27 + "[0m");
    }
    System.out.println(
        "] " + tagCount + " " + message + ((tagCount != 1) ? "s" : "") + " (see " + see + ")");

    // get all files with errors
    final List<String> offendingFiles = new ArrayList<>(tagCount);
    for (int i = 0; i < tagCount; ++i) {
      offendingFiles.add(i, filenameExtractor.apply(tags.item(i)));
    }

    // get uncommitted files
    final Repository repository =
        new FileRepositoryBuilder().readEnvironment().findGitDir(xml.getParentFile()).build();
    final Git git = new Git(repository);
    final Status status = git.status().call();

    final Set<String> changes = status.getUncommittedChanges();
    changes.addAll(status.getUntracked());

    int offendingChangedFileCount = 0;
    for (String file : changes) {
      String uncommitedChange =
          new File(repository.getDirectory().getParent(), file).getAbsolutePath();

      for (String offendingFile : offendingFiles) {
        int index = uncommitedChange.indexOf(offendingFile);
        if (index >= 0 && uncommitedChange.length() - index == offendingFile.length()) {
          ++offendingChangedFileCount;
          System.out.print("[");
          System.out.print((char) 27 + "[1;33mWARNING" + (char) 27 + "[0m");
          System.out.println("]   - " + uncommitedChange);
        }
      }
    }

    if (offendingChangedFileCount > 0) {
      System.out.print("[");
      System.out.print((char) 27 + "[1;33mWARNING" + (char) 27 + "[0m");
      System.out.println("] of which the above " + offendingChangedFileCount
          + (offendingChangedFileCount != 1 ? " are" : " is") + " in files you have changed");
    } else {
      System.out.print("[");
      System.out.print((char) 27 + "[1;34mINFO" + (char) 27 + "[0m");
      System.out.println("] of which 0 are in files you have changed");
    }
  }
}
