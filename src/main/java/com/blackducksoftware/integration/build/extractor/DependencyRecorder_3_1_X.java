/*******************************************************************************
 * Black Duck Software Suite SDK
 * Copyright (C) 2016 Black Duck Software, Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *******************************************************************************/
package com.blackducksoftware.integration.build.extractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.build.BuildArtifact;
import com.blackducksoftware.integration.build.BuildDependency;
import com.blackducksoftware.integration.build.BuildInfo;

@Component(role = EventSpy.class, isolatedRealm = true, description = "Build Info Recorder")
public class DependencyRecorder_3_1_X extends AbstractEventSpy {
	private Logger logger;
	private BuildInfo buildInfo = null;
	private String workingDirectory = null;

	@Override
	public void init(final Context context) throws Exception {
		logger = LoggerFactory.getLogger(DependencyRecorder_3_1_X.class);
		logger.info("Dependency Recorder for Maven 3.1");
		logger.debug("Context: ");
		String buildId = null;
		for (final Entry<String, Object> e : context.getData().entrySet()) {
			logger.debug("  " + e.getKey() + ": " + e.getValue());
			if (Recorder_3_1_Loader.CONTEXT_USER_PROPERTIES.equals(e.getKey())) {
				final Properties properties = (Properties) e.getValue();
				if (properties.getProperty(Recorder_3_1_Loader.PROPERTY_BUILD_ID) != null
						&& StringUtils.isEmpty(buildId)) {
					buildId = properties.getProperty(Recorder_3_1_Loader.PROPERTY_BUILD_ID);
					logger.info(Recorder_3_1_Loader.PROPERTY_BUILD_ID + ": " + buildId);
				}
			} else if (Recorder_3_1_Loader.PROPERTY_WORKING_DIRECTORY.equals(e.getKey())) {
				workingDirectory = (String) e.getValue();
				logger.info(Recorder_3_1_Loader.PROPERTY_WORKING_DIRECTORY + ": " + workingDirectory);
			} else if (Recorder_3_1_Loader.CONTEXT_SYSTEM_PROPERTIES.equals(e.getKey())) {
				final Properties properties = (Properties) e.getValue();
				if (properties.getProperty(Recorder_3_1_Loader.PROPERTY_BUILD_ID) != null
						&& StringUtils.isEmpty(buildId)) {
					buildId = properties.getProperty(Recorder_3_1_Loader.PROPERTY_BUILD_ID);
					logger.info(Recorder_3_1_Loader.PROPERTY_BUILD_ID + ": " + buildId);
				}
			}
		}
		if (StringUtils.isEmpty(buildId)) {
			logger.info("Could not find the property : " + Recorder_3_1_Loader.PROPERTY_BUILD_ID);
		}
		buildInfo = new BuildInfo();
		buildInfo.setBuildId(buildId);
		super.init(context);
	}

	@Override
	public void close() throws Exception {
		logger.info("Dependency Recorder: write " + workingDirectory + File.separator + BuildInfo.OUTPUT_FILE_NAME);
		buildInfo.close(new File(workingDirectory));
		super.close();
	}

	@Override
	public void onEvent(final Object event) throws Exception {
		if (event instanceof DependencyResolutionResult) {
			onEvent((DependencyResolutionResult) event);
		} else if (event instanceof ExecutionEvent) {
			onEvent((ExecutionEvent) event);
		}
	}

	/**
	 * When a DependencyResolutionResult event occurs during the build, the
	 * event dependencies are recorded to the build-info.json file using Json
	 *
	 * @param event
	 *            DependencyResolutionResult a dependency resolution event
	 *
	 * @throws Exception
	 */
	public void onEvent(final DependencyResolutionResult event) throws Exception {
		final List<Dependency> dependencies = event.getDependencies();
		logger.debug("onEvent(DependencyResolutionResult): " + event.getClass().getName() + "::" + event);
		logger.debug("Dependencies #: " + dependencies.size());
		final List<BuildDependency> projectDependencies = new ArrayList<BuildDependency>();
		for (final Dependency d : dependencies) {
			final BuildDependency currentDependency = new BuildDependency();
			final Artifact artifact = d.getArtifact();
			currentDependency.setGroup(artifact.getGroupId());
			currentDependency.setArtifact(artifact.getArtifactId());
			currentDependency.setVersion(artifact.getVersion());
			final ArrayList<String> scopes = new ArrayList<String>();
			scopes.add(d.getScope());
			currentDependency.setScope(scopes);
			currentDependency.setClassifier(artifact.getClassifier());
			currentDependency.setExtension(artifact.getExtension());
			projectDependencies.add(currentDependency);
		}
		buildInfo.addDependencies(projectDependencies);
	}

	public void onEvent(final ExecutionEvent event) throws Exception {
		logger.debug("onEvent(ExecutionEvent): " + event.getClass().getName() + "::" + event);
		final MavenProject project = event.getProject();
		if (project != null) {
			buildInfo.addArtifact(createBuildArtifact(project));
		}
	}

	private BuildArtifact createBuildArtifact(final MavenProject pom) {
		final BuildArtifact artifact = new BuildArtifact();
		artifact.setType(Recorder_3_1_Loader.MAVEN_TYPE);
		artifact.setGroup(pom.getGroupId());
		artifact.setArtifact(pom.getArtifactId());
		artifact.setVersion(pom.getVersion());
		artifact.setId(pom.getId());
		return artifact;
	}

}
