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

package org.springframework.graphql;

import java.util.List;

public class Book {

	Long id;

	String name;

	Long authorId;

	Author author;

	List<Long> genreIds;

	Store store;

	public Book() {
	}

	public Book(Long id, String name, Long authorId, List<Long> genreIds, Store store) {
		this.id = id;
		this.name = name;
		this.authorId = authorId;
		this.author = null;
		this.genreIds = genreIds;
		this.store = store;
	}

	public Book(Long id, String name, Author author, List<Genre> genres, Store store) {
		this.id = id;
		this.name = name;
		this.authorId = author.getId();
		this.author = author;
		this.genreIds = genres.stream().map(Genre::id).toList();
		this.store = store;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getAuthorId() {
		return this.authorId;
	}

	public Author getAuthor() {
		return this.author;
	}

	public void setAuthor(Author author) {
		this.author = author;
	}

	public List<Long> getGenreIds() {
		return this.genreIds;
	}

	public Store getStore() {
		return this.store;
	}
}
