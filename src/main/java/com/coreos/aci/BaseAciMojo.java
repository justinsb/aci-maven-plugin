package com.coreos.aci;

import java.io.File;
import java.net.URI;
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;
import com.coreos.appc.AciRepository;
import com.coreos.appc.S3AciRepository;
import com.coreos.maven.MavenLogAdapter;
import com.google.common.base.Strings;

public abstract class BaseAciMojo extends AbstractMojo {

  @Parameter(property = "project.build.directory")
  protected File projectBuildDirectory;

  @Parameter(defaultValue = "${project}")
  protected MavenProject mavenProject;

  @Parameter(readonly = true, defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repositorySystemSession;

  @Component
  private ProjectDependenciesResolver projectDependenciesResolver;

  /**
   * The ACI repository to push to. Must be set for a push.
   */
  @Parameter(property = "repository")
  protected String aciRepository;

  protected AciRepository getAciRepository() throws MojoExecutionException {
    if (Strings.isNullOrEmpty(this.aciRepository)) {
      throw new MojoExecutionException("Must set repository to push to");
    }
    URI uri = URI.create(this.aciRepository);
    String scheme = uri.getScheme();
    if (scheme.equals("s3")) {
      AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
      AmazonS3Client amazonS3Client = new AmazonS3Client(provider);
      String bucketName = uri.getHost();
      String prefix = uri.getPath();
      return new S3AciRepository(amazonS3Client, bucketName, prefix);
    } else {
      throw new MojoExecutionException("Unknown repository scheme: " + scheme);
    }
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

  protected Logger getSlf4jLogger() {
    return new MavenLogAdapter(getLog(), getLog().isDebugEnabled());
  }

  protected BuildType detectBuildType() throws MojoExecutionException {
    Artifact mainArtifact = mavenProject.getArtifact();
    if (mainArtifact == null) {
      throw new MojoExecutionException("Could not find primary artifact");
    }
    String artifactType = mainArtifact.getType();
    if ("war".equals(artifactType)) {
      return BuildType.WAR;
    } else if ("jar".equals(artifactType)) {
      return BuildType.JAR;
    } else {
      getLog().warn("Unknown artifact type: " + artifactType);
      return null;
    }
  }

}
