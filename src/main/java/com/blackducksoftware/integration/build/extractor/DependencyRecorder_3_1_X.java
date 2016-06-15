/*******************************************************************************
 * Copyright (C) 2016 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package com.blackducksoftware.integration.build.extractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.build.BuildArtifact;
import com.blackducksoftware.integration.build.BuildDependency;
import com.blackducksoftware.integration.build.BuildInfo;
import com.blackducksoftware.integration.build.bdio.BdioConverter;
import com.blackducksoftware.integration.build.bdio.CommonBomFormatter;
import com.blackducksoftware.integration.build.bdio.Gav;
import com.blackducksoftware.integration.build.bdio.MavenIdCreator;

@Component(role = EventSpy.class, isolatedRealm = true, description = "Build Info Recorder")
public class DependencyRecorder_3_1_X extends AbstractEventSpy {
	private static final String TARGET = "target";
	private static final String BDIO_FILE_SUFFIX = "_bdio.json";

	private Logger logger;
	private String buildId;
	private String workingDirectory;
	private MavenProject project;
	private List<Dependency> dependencies;
	private DependencyNode rootMavenDependencyNode;

	@Override
	public void init(final Context context) throws Exception {
		logger = LoggerFactory.getLogger(DependencyRecorder_3_1_X.class);
		logger.info("Dependency Recorder for Maven 3.1");

		final Map<String, Object> data = context.getData();
		final Properties contextUserProperties = (Properties) data.get(Recorder_3_1_Loader.CONTEXT_USER_PROPERTIES);
		final Properties contextSystemProperties = (Properties) data.get(Recorder_3_1_Loader.CONTEXT_SYSTEM_PROPERTIES);

		buildId = contextUserProperties.getProperty(Recorder_3_1_Loader.PROPERTY_BUILD_ID);
		if (StringUtils.isEmpty(buildId)) {
			buildId = contextSystemProperties.getProperty(Recorder_3_1_Loader.PROPERTY_BUILD_ID);
		}

		if (StringUtils.isEmpty(buildId)) {
			logger.info("Could not find the property : " + Recorder_3_1_Loader.PROPERTY_BUILD_ID);
		} else {
			logger.info(Recorder_3_1_Loader.PROPERTY_BUILD_ID + ": " + buildId);
		}

		workingDirectory = (String) data.get(Recorder_3_1_Loader.PROPERTY_WORKING_DIRECTORY);
		logger.info(Recorder_3_1_Loader.PROPERTY_WORKING_DIRECTORY + ": " + workingDirectory);

		super.init(context);
	}

	@Override
	public void close() throws Exception {
		handleBdioOutput();
		handleBuildInfoOutput();

		super.close();
	}

	@Override
	public void onEvent(final Object event) throws Exception {
		logger.debug("onEvent: " + event.getClass().getName() + "::" + event);
		if (event instanceof DependencyResolutionResult) {
			rootMavenDependencyNode = ((DependencyResolutionResult) event).getDependencyGraph();
			dependencies = ((DependencyResolutionResult) event).getDependencies();
		} else if (event instanceof ExecutionEvent) {
			final MavenProject eventProject = ((ExecutionEvent) event).getProject();
			if (null != eventProject) {
				project = eventProject;
			}
		}
	}

	private void handleBuildInfoOutput() throws IOException {
		logger.info("creating build-info output");
		final BuildArtifact buildArtifact = createBuildArtifact();

		final BuildInfo buildInfo = new BuildInfo();
		buildInfo.setBuildArtifact(buildArtifact);

		final Set<BuildDependency> projectDependencies = new HashSet<BuildDependency>();
		logger.debug("Dependencies #: " + dependencies.size());
		for (final Dependency d : dependencies) {
			final BuildDependency currentDependency = new BuildDependency();
			final Artifact artifact = d.getArtifact();
			currentDependency.setGroup(artifact.getGroupId());
			currentDependency.setArtifact(artifact.getArtifactId());
			currentDependency.setVersion(artifact.getVersion());
			final Set<String> scopes = new HashSet<String>();
			scopes.add(d.getScope());
			currentDependency.setScopes(scopes);
			currentDependency.setClassifier(artifact.getClassifier());
			currentDependency.setExtension(artifact.getExtension());
			projectDependencies.add(currentDependency);
		}
		buildInfo.setDependencies(projectDependencies);

		logger.info("Dependency Recorder: write " + workingDirectory + File.separator + BuildInfo.OUTPUT_FILE_NAME);
		buildInfo.close(new File(workingDirectory));
	}

	private BuildArtifact createBuildArtifact() {
		final BuildArtifact artifact = new BuildArtifact();
		artifact.setType(Recorder_3_1_Loader.MAVEN_TYPE);
		artifact.setGroup(project.getGroupId());
		artifact.setArtifact(project.getArtifactId());
		artifact.setVersion(project.getVersion());
		return artifact;
	}

	private void handleBdioOutput() throws IOException {
		logger.info("creating bdio output");
		final Gav projectGav = new Gav(project.getGroupId(), project.getArtifactId(), project.getVersion());

		File file = new File(workingDirectory);
		file = new File(file, TARGET);
		file = new File(file, projectGav.getArtifactId() + BDIO_FILE_SUFFIX);

		try (final OutputStream outputStream = new FileOutputStream(file)) {
			final com.blackducksoftware.integration.build.bdio.DependencyNode root = createCommonDependencyNode(
					rootMavenDependencyNode);
			final MavenIdCreator mavenIdCreator = new MavenIdCreator();
			final BdioConverter bdioConverter = new BdioConverter(mavenIdCreator);
			final CommonBomFormatter commonBomFormatter = new CommonBomFormatter(bdioConverter);
			commonBomFormatter.writeProject(outputStream, project.getName(), root);
			logger.info("Created Black Duck I/O json: " + file.getAbsolutePath());
		}
	}

	private com.blackducksoftware.integration.build.bdio.DependencyNode createCommonDependencyNode(
			final DependencyNode mavenDependencyNode) {
		final String groupId = mavenDependencyNode.getArtifact().getGroupId();
		final String artifactId = mavenDependencyNode.getArtifact().getArtifactId();
		final String version = mavenDependencyNode.getArtifact().getVersion();
		final Gav gav = new Gav(groupId, artifactId, version);
		final List<com.blackducksoftware.integration.build.bdio.DependencyNode> children = new ArrayList<>();
		for (final DependencyNode child : mavenDependencyNode.getChildren()) {
			children.add(createCommonDependencyNode(child));
		}

		return new com.blackducksoftware.integration.build.bdio.DependencyNode(gav, children);
	}

}
