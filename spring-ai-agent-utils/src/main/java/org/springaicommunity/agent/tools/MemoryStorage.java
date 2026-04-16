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
import java.util.List;

/**
 * Storage provider for AutoMemoryTools.
 * Encapsulates all persistent storage operations (read, write, delete, list).
 * All paths are relative to the memories root.
 *
 * @author ichaobuster
 */
public interface MemoryStorage {

	/**
	 * Checks if the given path exists in the memory store.
	 * 
	 * @param path the path relative to the memories root.
	 * @return true if the path exists.
	 */
	boolean exists(String path);

	/**
	 * Checks if the given path is a directory.
	 * 
	 * @param path the path relative to the memories root.
	 * @return true if it is a directory.
	 */
	boolean isDirectory(String path);

	/**
	 * Lists the contents of a directory in the memory store.
	 * 
	 * @param path the directory path relative to the memories root.
	 * @return a formatted string listing the contents.
	 * @throws IOException if an error occurs.
	 */
	String listDirectory(String path) throws IOException;

	/**
	 * Reads the entire content of a file as a string.
	 * 
	 * @param path the file path relative to the memories root.
	 * @return the file content.
	 * @throws IOException if an error occurs.
	 */
	String readString(String path) throws IOException;

	/**
	 * Reads all lines of a file.
	 * 
	 * @param path the file path relative to the memories root.
	 * @return the list of lines.
	 * @throws IOException if an error occurs.
	 */
	List<String> readAllLines(String path) throws IOException;

	/**
	 * Writes a string to a file. Overwrites if it exists.
	 * 
	 * @param path    the file path relative to the memories root.
	 * @param content the content to write.
	 * @throws IOException if an error occurs.
	 */
	void writeString(String path, String content) throws IOException;

	/**
	 * Deletes a file or directory (recursively) from the memory store.
	 * 
	 * @param path the path relative to the memories root.
	 * @throws IOException if an error occurs.
	 */
	void delete(String path) throws IOException;

	/**
	 * Renames or moves a file or directory.
	 * 
	 * @param oldPath current path.
	 * @param newPath new path.
	 * @throws IOException if an error occurs.
	 */
	void rename(String oldPath, String newPath) throws IOException;

}
