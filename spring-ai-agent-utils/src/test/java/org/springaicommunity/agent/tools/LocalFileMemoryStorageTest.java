/*
 * Copyright 2025 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.agent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LocalFileMemoryStorage}.
 *
 * @author ichaobuster
 */
@DisplayName("LocalFileMemoryStorage Tests")
class LocalFileMemoryStorageTest {

	@TempDir
	Path tempDir;

	private LocalFileMemoryStorage storage;

	@BeforeEach
	void setUp() {
		storage = new LocalFileMemoryStorage(tempDir);
	}

	@Test
	@DisplayName("exists() returns true for existing file")
	void exists() throws IOException {
		Path file = tempDir.resolve("test.txt");
		Files.writeString(file, "content");
		assertThat(storage.exists("test.txt")).isTrue();
		assertThat(storage.exists("missing.txt")).isFalse();
	}

	@Test
	@DisplayName("isDirectory() returns true for directory")
	void isDirectory() throws IOException {
		Path dir = tempDir.resolve("subdir");
		Files.createDirectory(dir);
		assertThat(storage.isDirectory("subdir")).isTrue();
		Files.writeString(tempDir.resolve("file.txt"), "x");
		assertThat(storage.isDirectory("file.txt")).isFalse();
	}

	@Test
	@DisplayName("listDirectory() returns formatted listing")
	void listDirectory() throws IOException {
		Files.writeString(tempDir.resolve("a.txt"), "a");
		Path sub = tempDir.resolve("sub");
		Files.createDirectory(sub);
		Files.writeString(sub.resolve("b.txt"), "bb");

		String listing = storage.listDirectory("");
		assertThat(listing).contains("a.txt (1 bytes)").contains("sub/").contains("b.txt (2 bytes)");
	}

	@Test
	@DisplayName("readString() and writeString() work correctly")
	void readWrite() throws IOException {
		storage.writeString("new.txt", "hello");
		assertThat(storage.readString("new.txt")).isEqualTo("hello");
		assertThat(Files.readString(tempDir.resolve("new.txt"))).isEqualTo("hello");
	}

	@Test
	@DisplayName("readAllLines() works correctly")
	void readAllLines() throws IOException {
		storage.writeString("lines.txt", "1\n2\n3");
		List<String> lines = storage.readAllLines("lines.txt");
		assertThat(lines).containsExactly("1", "2", "3");
	}

	@Test
	@DisplayName("delete() removes files and directories")
	void delete() throws IOException {
		storage.writeString("to-delete.txt", "x");
		storage.delete("to-delete.txt");
		assertThat(tempDir.resolve("to-delete.txt")).doesNotExist();

		Path sub = tempDir.resolve("sub");
		Files.createDirectory(sub);
		Files.writeString(sub.resolve("f.txt"), "x");
		storage.delete("sub");
		assertThat(sub).doesNotExist();
	}

	@Test
	@DisplayName("rename() moves files")
	void rename() throws IOException {
		storage.writeString("old.txt", "data");
		storage.rename("old.txt", "new.txt");
		assertThat(tempDir.resolve("old.txt")).doesNotExist();
		assertThat(tempDir.resolve("new.txt")).exists();
	}

	@Test
	@DisplayName("Path traversal is blocked")
	void pathTraversal() {
		assertThatThrownBy(() -> storage.exists("../passwd"))
				.isInstanceOf(SecurityException.class)
				.hasMessageContaining("Path traversal attempt detected");
	}

	@Test
	@DisplayName("Absolute paths are blocked")
	void absolutePaths() {
		assertThatThrownBy(() -> storage.exists("/etc/passwd"))
				.isInstanceOf(SecurityException.class)
				.hasMessageContaining("Absolute paths are not allowed");
	}

	@Test
	@DisplayName("exists() with root path variants")
	void existsRoot() {
		assertThat(storage.exists("")).isTrue();
		assertThat(storage.exists("/")).isTrue();
	}

	@Test
	@DisplayName("listDirectory() with deep nesting")
	void listDirectoryDeep() throws IOException {
		Path d1 = tempDir.resolve("d1");
		Files.createDirectory(d1);
		Path d2 = d1.resolve("d2");
		Files.createDirectory(d2);
		Files.writeString(d2.resolve("f.txt"), "leaf");

		String listing = storage.listDirectory("d1");
		assertThat(listing).contains("d2/");
		assertThat(listing).contains("f.txt");
	}

	@Test
	@DisplayName("delete() root should throw SecurityException")
	void deleteRoot() {
		assertThatThrownBy(() -> storage.delete("/"))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> storage.delete(""))
				.isInstanceOf(SecurityException.class);
	}

	@Test
	@DisplayName("rename() to non-existent parent creates parents")
	void renameCreatesParents() throws IOException {
		storage.writeString("f.txt", "x");
		storage.rename("f.txt", "a/b/c.txt");
		assertThat(tempDir.resolve("a/b/c.txt")).exists();
	}

}
