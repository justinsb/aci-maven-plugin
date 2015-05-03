package com.coreos.aci;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;

import com.coreos.maven.MavenLogAdapter;

/**
 * Maven-specific helpers
 * 
 */
public abstract class BaseMojo extends AbstractMojo {

  @Parameter(property = "project.build.directory")
  protected File projectBuildDirectory;

  @Parameter(defaultValue = "${project}")
  protected MavenProject mavenProject;

  @Parameter(readonly = true, defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repositorySystemSession;

  @Component
  private ProjectDependenciesResolver projectDependenciesResolver;

  protected Logger getSlf4jLogger() {
    return new MavenLogAdapter(getLog(), getLog().isDebugEnabled());
  }

  protected Artifact getMainArtifact() throws MojoExecutionException {
    Artifact mainArtifact = mavenProject.getArtifact();
    if (mainArtifact == null) {
      throw new MojoExecutionException("Could not find primary artifact");
    }
    return mainArtifact;
  }

  protected List<Artifact> getDependencyArtifacts() throws MojoExecutionException {
    DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(mavenProject,
        repositorySystemSession);
    DependencyResolutionResult result;

    try {
      result = projectDependenciesResolver.resolve(request);
    } catch (DependencyResolutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }

    List<Artifact> artifacts = new ArrayList<>();
    List<DependencyNode> dependencyNodes = new ArrayList<>();
    if (result.getDependencyGraph() != null) {
      for (DependencyNode dependencyNode : result.getDependencyGraph().getChildren()) {
        String scope = null;
        Dependency dependency = dependencyNode.getDependency();
        if (dependency != null) {
          scope = dependency.getScope();
        }
        if (scope != null) {
          if (scope.equals("test")) {
            getLog().debug("Skipping test dependency: " + dependencyNode);
            continue;
          }
        }
        dependencyNodes.add(dependencyNode);
      }
    } else {
      getLog().warn("Could not resolve dependencies (dependency-graph null)");
    }
    if (!dependencyNodes.isEmpty()) {
      List<String> trail = Collections.singletonList(mavenProject.getArtifact().getId());
      RepositoryUtils.toArtifacts(artifacts, dependencyNodes, trail, null);
    }
    return artifacts;
  }
}
