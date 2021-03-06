/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.deployer.spi.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.task.LaunchState.complete;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;
import org.springframework.core.io.Resource;

/**
 * Abstract base class for integration tests of
 * {@link org.springframework.cloud.deployer.spi.task.TaskLauncher} implementations.
 * <p>
 * Inheritors should setup an environment with a newly created
 * {@link org.springframework.cloud.deployer.spi.task.TaskLauncher}.
 *
 * Tests in this class are independent and leave the
 * launcher in a clean state after they successfully run.
 * </p>
 * <p>
 * As deploying a task is often quite time consuming, some tests assert
 * various aspects of deployment in a row, to avoid re-deploying apps over and
 * over again.
 * </p>
 *
 * @author Eric Bottard
 */
public abstract class AbstractTaskLauncherIntegrationTests extends AbstractIntegrationTests {


	protected abstract TaskLauncher taskLauncher();

	@Test
	public void testNonExistentAppsStatus() {
		assertThat(randomName(), hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", is(LaunchState.unknown))));
	}

	@Test
	public void testSimpleLaunch() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = record(taskLauncher().launch(request));

		Timeout timeout = deploymentTimeout();
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

	}

	@Test
	public void testReLaunch() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = record(taskLauncher().launch(request));

		Timeout timeout = deploymentTimeout();
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

		log.info("Re-Launching {}...", request.getDefinition().getName());
		String newLaunchId = record(taskLauncher().launch(request));

		assertThat(newLaunchId, not(is(launchId)));

		timeout = deploymentTimeout();
		assertThat(newLaunchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.complete))), timeout.maxAttempts, timeout.pause));

	}

	@Test
	public void testErrorExit() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "0");
		appProperties.put("exitCode", "1");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = record(taskLauncher().launch(request));

		Timeout timeout = deploymentTimeout();
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.failed))), timeout.maxAttempts, timeout.pause));

	}

	@Test
	public void testSimpleCancel() throws InterruptedException {
		Map<String, String> appProperties = new HashMap<>();
		appProperties.put("killDelay", "-1");
		appProperties.put("exitCode", "0");
		AppDefinition definition = new AppDefinition(randomName(), appProperties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

		log.info("Launching {}...", request.getDefinition().getName());
		String launchId = record(taskLauncher().launch(request));

		Timeout timeout = deploymentTimeout();
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.running))), timeout.maxAttempts, timeout.pause));

		log.info("Cancelling {}...", request.getDefinition().getName());
		taskLauncher().cancel(launchId);

		timeout = undeploymentTimeout();
		assertThat(launchId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(LaunchState.cancelled))), timeout.maxAttempts, timeout.pause));

	}

	/**
	 * Tests that command line args can be passed in.
	 */
	@Test
	public void testCommandLineArgs() {
		Map<String, String> properties = new HashMap<>();
		properties.put("killDelay", "1000");
		AppDefinition definition = new AppDefinition(randomName(), properties);
		Resource resource = testApplication();
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, Collections.<String, String>emptyMap(),
				Collections.singletonList("--exitCode=0"));
		log.info("Launching {}...", request.getDefinition().getName());
		String deploymentId = record(taskLauncher().launch(request));

		Timeout timeout = deploymentTimeout();
		assertThat(deploymentId, eventually(hasStatusThat(
				Matchers.<TaskStatus>hasProperty("state", Matchers.is(complete))), timeout.maxAttempts, timeout.pause));
	}


	/**
	 * A Hamcrest Matcher that queries the deployment status for some task id.
	 */
	protected Matcher<String> hasStatusThat(final Matcher<TaskStatus> statusMatcher) {
		return new BaseMatcher<String>() {

			private TaskStatus status;

			@Override
			public boolean matches(Object item) {
				status = taskLauncher().status((String) item);
				return statusMatcher.matches(status);
			}

			@Override
			public void describeMismatch(Object item, Description mismatchDescription) {
				mismatchDescription.appendText("status of ").appendValue(item).appendText(" ");
				statusMatcher.describeMismatch(status, mismatchDescription);
			}


			@Override
			public void describeTo(Description description) {
				statusMatcher.describeTo(description);
			}
		};
	}

}

