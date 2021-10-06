/*
 * Copyright 2020-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.test.tester;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

/**
 * Default implementation for {@link QueryNameResolver} that looks up
 * query files in the classpath based on the given query name,
 * and configured locations and file extensions.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 1.0.0
 */
public class DefaultQueryNameResolver implements QueryNameResolver {

	private static final Log logger = LogFactory.getLog(DefaultQueryNameResolver.class);

	private static final List<Resource> DEFAULT_LOCATION = Collections.singletonList(new ClassPathResource("graphql/"));

	private static final String[] DEFAULT_EXTENSIONS = new String[] {".graphql", ".gql"};

	private final List<Resource> locations;

	private final String[] extensions;


	public DefaultQueryNameResolver() {
		this(DEFAULT_LOCATION, DEFAULT_EXTENSIONS);
	}

	public DefaultQueryNameResolver(List<Resource> locations, String[] extensions) {
		Assert.notEmpty(locations, "should provide at least one location");
		Assert.notNull(extensions, "extensions are required");
		this.locations = locations;
		this.extensions = extensions;
	}

	@Override
	public String resolveQuery(String queryName) {
		Resource queryResource = getQueryResource(queryName);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			FileCopyUtils.copy(queryResource.getInputStream(), outputStream);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Failed to read query from: " + queryResource.getDescription());
		}
		return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
	}

	private Resource getQueryResource(String queryName) {
		for (Resource location : this.locations) {
			for (String extension : this.extensions) {
				try {
					Resource resource = location.createRelative(queryName + extension);
					if (resource.exists()) {
						return resource;
					}
				}
				catch (IOException exc) {
					logger.debug("Could not find '" + queryName + extension + "' in location " + location.getDescription());
				}
			}
		}
		throw new IllegalArgumentException(
				"Could not find file '" + queryName + "' with extensions " + Arrays.toString(this.extensions) +
						" under " + getLocationsDescription());
	}

	private String getLocationsDescription() {
		return Arrays.toString(this.locations.stream().map(Resource::getDescription).toArray(String[]::new));
	}
}
