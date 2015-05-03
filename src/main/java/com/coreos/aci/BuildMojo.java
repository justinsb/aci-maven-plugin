package com.coreos.aci;

import com.coreos.appc.AppcContainerBuilder;
import com.coreos.appc.ContainerBuilder;
import com.coreos.appc.ContainerFile;
import com.coreos.maven.MavenLogAdapter;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used to build ACI images.
 */
@Mojo(name = "build")
public class BuildMojo extends BaseAciMojo {

  /** The base image to use. */
  @Parameter(property = "baseImage")
  private String baseImage;

  /** The created image will be given this name. */
  @Parameter(property = "imageName")
  private String imageName;

  /** The cmd command for the image. */
  @Parameter(property = "cmd")
  private String cmd;

  @Parameter(defaultValue = "${project}")
  private MavenProject mavenProject;

  @Parameter(readonly = true, defaultValue = "${repositorySystemSession}")
  private RepositorySystemSession repositorySystemSession;

  @Component
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Override
  public void execute() throws MojoExecutionException {
    // final File workdir = new File(projectBuildDirectory, "aci");

    File imageFile = new File(projectBuildDirectory, "image.aci");
    ContainerBuilder builder = new AppcContainerBuilder(imageFile);
    builder.log = getSlf4jLogger();
    builder.baseImage = baseImage;
    builder.cmd = cmd;
    // builder.env = env;
    // builder.entryPoint = entryPoint;
    // builder.exposesSet = exposesSet;
    // builder.maintainer = maintainer;

    try {
      copyArtifacts(builder);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy artifacts", e);
    }

    // copyResources(builder);

    String imageVersion = mavenProject.getVersion();

    try {
      builder.buildImage(imageName, imageVersion);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to build image", e);
    }

    // if (pushImage) {
    // if (builder instanceof DockerContainerBuilder) {
    // pushImage(docker, imageName, getLog());
    // } else if (builder instanceof AppcContainerBuilder) {
    // AciRepository aciRepository = getAciRepository();
    // AciImageInfo imageInfo = new AciImageInfo();
    // imageInfo.name = imageName;
    // imageInfo.version = imageVersion;
    //
    // byte[] signature = null;
    // if (signImage) {
    // AciSigner signer = new GpgCommandAciSigner();
    // signature = signer.sign(imageFile);
    // }
    //
    // aciRepository.push(imageInfo, imageFile, signature, getSlf4jLogger());
    // } else {
    // throw new IllegalStateException();
    // }
    // }
  }

  private Logger getSlf4jLogger() {
    return new MavenLogAdapter(getLog(), getLog().isDebugEnabled());
  }

  public List<Artifact> getDependencyArtifacts() throws MojoExecutionException {
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

  private void copyArtifacts(ContainerBuilder builder) throws IOException, MojoExecutionException {
    List<ContainerFile> containerFiles = new ArrayList<>();

    Artifact mainArtifact = mavenProject.getArtifact();
    if (mainArtifact == null) {
      throw new MojoExecutionException("Could not find primary artifact");
    }
    File file = mainArtifact.getFile();
    if (file == null) {
      throw new MojoExecutionException("No file found for primary artifact (" + mainArtifact + ")");
    }
    String targetName = file.getName();

    boolean copyDependencies = false;
    String artifactType = mainArtifact.getType();
    if ("war".equals(artifactType)) {
      targetName = "root.war";
    } else if ("jar".equals(artifactType)) {
      targetName = "app.jar";
      copyDependencies = true;
    } else {
      getLog().warn("Unknown artifact type: " + artifactType);
    }

    String imagePath = "/app/" + targetName;

    ContainerFile containerFile = new ContainerFile(file.toPath(), imagePath);
    getLog().debug("Copying " + file + " to " + imagePath);
    containerFiles.add(containerFile);

    if (copyDependencies) {
      List<Artifact> runtimeDependencies = getDependencyArtifacts();
      for (Artifact runtimeDependency : runtimeDependencies) {
        file = runtimeDependency.getFile();
        imagePath = "/app/lib/" + file.getName();
        containerFile = new ContainerFile(file.toPath(), imagePath);
        containerFiles.add(containerFile);
      }
    }

    builder.addFiles(containerFiles);
  }
}
