/*
 * Copyright 2020-2024 the original author or authors.
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

package org.springframework.graphql.data.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graphql.schema.FieldCoordinates;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class ComplexityScore {

	private final FieldCoordinates fieldCoordinates;

	private final int fieldScore;

	private final List<ComplexityScore> children = new ArrayList<>();

	@Nullable
	private final String fragmentType;

	private final int elementCount;

	private final boolean isBatched;

	public static Builder create(FieldCoordinates fieldCoordinates) {
		return new Builder(fieldCoordinates);
	}

	public static ComplexityScore createTrivial(FieldCoordinates fieldCoordinates) {
		return new ComplexityScore(fieldCoordinates, 0, null, 1, false);
	}

	ComplexityScore(FieldCoordinates fieldCoordinates, int fieldScore, String fragmentType,
					int elementCount, boolean isBatched) {
		Assert.notNull(fieldCoordinates, "FieldCoordinates must not be null");
		this.fieldCoordinates = fieldCoordinates;
		this.fieldScore = fieldScore;
		this.fragmentType = fragmentType;
		this.elementCount = elementCount;
		this.isBatched = isBatched;
	}

	public int calculate() {
		if (this.elementCount > 1 && !this.isBatched) {
			return this.fieldScore + (this.elementCount * this.calculateChildScore());
		}
		return this.fieldScore + this.calculateChildScore();
	}

	public FieldCoordinates getFieldCoordinates() {
		return this.fieldCoordinates;
	}

	public int getFieldScore() {
		return this.fieldScore;
	}

	@Nullable
	public String getFragmentType() {
		return this.fragmentType;
	}

	public boolean isBatched() {
		return this.isBatched;
	}

	public int getElementCount() {
		return this.elementCount;
	}

	public void addChild(ComplexityScore child) {
		this.children.add(child);
	}


	private int calculateChildScore() {
		int fragmentScore = 0;
		int fieldsScore = 0;
		Map<String, Integer> fragmentScores = new HashMap<>(2);

		for (ComplexityScore child : this.children) {
			if (child.fragmentType != null) {
				fragmentScores.compute(child.fragmentType, (k,v) -> (v == null) ? child.calculate() : v + child.calculate());
			}
			else {
				fieldsScore += child.calculate();
			}
		}
		for (String fragmentType : fragmentScores.keySet()) {
			fragmentScore = Math.max(fragmentScore, fragmentScores.get(fragmentType));
		}

		return fragmentScore + fieldsScore;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		appendDescription(builder, 0);
		return builder.toString();
	}

	public void appendDescription(StringBuilder builder, int offset) {
		StringBuilder current = new StringBuilder();
		current.append(this.fieldCoordinates.toString())
				.append("[score=")
				.append(this.fieldScore);
		if (this.elementCount > 0) {
			current.append(", elementCount=").append(this.elementCount);
		}
		if (this.fragmentType != null) {
			current.append(", fragment=" + this.fragmentType);
		}
		if (this.isBatched) {
			current.append(", batched");
		}
		current.append(']');
		builder.append(current.toString().indent(offset));
		for (ComplexityScore child : children) {
			child.appendDescription(builder, offset + 2);
		}
	}

	static class Builder {

		private final FieldCoordinates fieldCoordinates;

		private int fieldScore;

		@Nullable
		private String fragmentType;

		private int elementCount = 1;

		private boolean isBatched;

		Builder(FieldCoordinates fieldCoordinates) {
			this.fieldCoordinates = fieldCoordinates;
		}

		public Builder fieldScore(int fieldScore) {
			this.fieldScore = fieldScore;
			return this;
		}

		public Builder fragmentType(String fragmentType) {
			this.fragmentType = fragmentType;
			return this;
		}

		public Builder elementCount(int elementCount) {
			this.elementCount = elementCount;
			return this;
		}
		public Builder isBatched(boolean isBatched) {
			this.isBatched = isBatched;
			return this;
		}

		public ComplexityScore build() {
			return new ComplexityScore(this.fieldCoordinates, this.fieldScore,
					this.fragmentType, this.elementCount, this.isBatched);
		}
	}

}
