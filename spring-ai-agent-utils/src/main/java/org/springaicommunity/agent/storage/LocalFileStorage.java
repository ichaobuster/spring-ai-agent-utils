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
package org.springaicommunity.agent.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Local file system implementation of the StorageProvider.
 * Uses java.nio.file.Files and Path APIs to manage files.
 *
 * @author ichaobuster
 */
public class LocalFileStorage implements StorageProvider {

	private final Path baseDir;

	public LocalFileStorage(Path baseDir) {
		Assert.notNull(baseDir, "baseDir must not be null");
		this.baseDir = baseDir.normalize();
		try {
			Files.createDirectories(this.baseDir);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create base directory: " + this.baseDir, e);
		}
	}

	public Path getBaseDir() {
		return this.baseDir;
	}

	@Override
	public boolean exists(String path) {
		return Files.exists(resolveSafePath(path));
	}

	@Override
	public boolean isDirectory(String path) {
		return Files.isDirectory(resolveSafePath(path));
	}

	@Override
	public String listDirectory(String path) throws IOException {
		Path dir = resolveSafePath(path);
		StringBuilder sb = new StringBuilder();
		String displayPath = !StringUtils.hasText(path) || path.equals("/") ? "/" : path;
		sb.append("Contents of ").append(displayPath).append(":\n\n");

		try (Stream<Path> level1 = Files.list(dir)) {
			List<Path> entries = level1.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					sb.append("  ").append(name).append("/\n");
					try (Stream<Path> level2 = Files.list(entry)) {
						List<Path> subEntries = level2.sorted(Comparator.comparing(p -> p.getFileName().toString()))
								.toList();
						for (Path sub : subEntries) {
							String subName = sub.getFileName().toString();
							if (Files.isDirectory(sub)) {
								sb.append("    ").append(subName).append("/\n");
							} else {
								long size = Files.size(sub);
								sb.append("    ").append(subName).append(" (").append(size).append(" bytes)\n");
							}
						}
					}
				} else {
					long size = Files.size(entry);
					sb.append("  ").append(name).append(" (").append(size).append(" bytes)\n");
				}
			}
		}
		return sb.toString();
	}

	@Override
	public String readString(String path) throws IOException {
		return Files.readString(resolveSafePath(path), StandardCharsets.UTF_8);
	}

	@Override
	public List<String> readAllLines(String path) throws IOException {
		return Files.readAllLines(resolveSafePath(path), StandardCharsets.UTF_8);
	}

	@Override
	public void writeString(String path, String content) throws IOException {
		Path target = resolveSafePath(path);
		Path parent = target.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}
		Files.writeString(target, content != null ? content : "", StandardCharsets.UTF_8);
	}

	@Override
	public void delete(String path) throws IOException {
		Path target = resolveSafePath(path);
		if (target.equals(this.baseDir)) {
			throw new SecurityException("Cannot delete the base root directory.");
		}

		if (Files.isDirectory(target)) {
			try (Stream<Path> walk = Files.walk(target)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					} catch (IOException e) {
						throw new RuntimeException("Failed to delete: " + p, e);
					}
				});
			}
		} else {
			Files.delete(target);
		}
	}

	@Override
	public void rename(String oldPath, String newPath) throws IOException {
		Path source = resolveSafePath(oldPath);
		Path destination = resolveSafePath(newPath);

		Path destParent = destination.getParent();
		if (destParent != null && !Files.exists(destParent)) {
			Files.createDirectories(destParent);
		}

		Files.move(source, destination);
	}

	/**
	 * Resolves a user-supplied relative path against the base directory,
	 * guarding against path traversal attacks and absolute path injection.
	 */
	private Path resolveSafePath(String relativePath) {
		if (!StringUtils.hasText(relativePath) || relativePath.equals("/")) {
			return this.baseDir;
		}
		Path userPath = Paths.get(relativePath);
		if (userPath.isAbsolute()) {
			throw new SecurityException("Absolute paths are not allowed: '" + relativePath + "'");
		}
		Path resolved = this.baseDir.resolve(userPath).normalize();
		if (!resolved.startsWith(this.baseDir)) {
			throw new SecurityException(
					"Path traversal attempt detected: '" + relativePath + "' escapes the base directory");
		}
		return resolved;
	}

}
