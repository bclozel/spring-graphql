/*
 * Copyright 2002-2021 the original author or authors.
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
package org.springframework.graphql.execution;

import java.util.Map;

import org.springframework.util.Assert;

/**
 * {@link ThreadLocalAccessor} that operates on the ThreadLocal it is given.
 */
class TestThreadLocalAccessor implements ThreadLocalAccessor {

	private final ThreadLocal<String> threadLocal;


	TestThreadLocalAccessor(ThreadLocal<String> threadLocal) {
		this.threadLocal = threadLocal;
	}


	@Override
	public void extractValues(Map<String, Object> container) {
		String name = this.threadLocal.get();
		Assert.notNull(name, "No ThreadLocal value");
		container.put("name", name);
	}

	@Override
	public void restoreValues(Map<String, Object> values) {
		String name = (String) values.get("name");
		Assert.notNull(name, "No value to set");
		this.threadLocal.set(name);
	}

	@Override
	public void resetValues(Map<String, Object> values) {
		this.threadLocal.remove();
	}
}
