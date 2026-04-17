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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Aliyun OSS implementation of the StorageProvider.
 * Uses the Aliyun OSS SDK to manage files.
 *
 * @author ichaobuster
 */
public class AliyunOssStorage implements StorageProvider {

	private final OSS ossClient;

	private final String bucketName;

	private final String prefix;

	public AliyunOssStorage(OSS ossClient, String bucketName, String prefix) {
		Assert.notNull(ossClient, "ossClient must not be null");
		Assert.hasText(bucketName, "bucketName must not be empty");
		this.ossClient = ossClient;
		this.bucketName = bucketName;
		this.prefix = StringUtils.hasText(prefix) ? (prefix.endsWith("/") ? prefix : prefix + "/") : "";
	}

	@Override
	public boolean exists(String path) {
		return this.ossClient.doesObjectExist(this.bucketName, getFullKey(path));
	}

	@Override
	public boolean isDirectory(String path) {
		// In OSS, a path is a directory if there are objects with this prefix ending in
		// '/'
		// or if we treat the prefix itself as a directory.
		String key = getFullKey(path);
		if (!key.endsWith("/")) {
			key += "/";
		}
		return this.ossClient.listObjects(new ListObjectsRequest(this.bucketName).withPrefix(key).withMaxKeys(1))
				.getObjectSummaries()
				.size() > 0;
	}

	@Override
	public String listDirectory(String path) throws IOException {
		String displayPath = !StringUtils.hasText(path) || path.equals("/") ? "/" : path;
		StringBuilder sb = new StringBuilder();
		sb.append("Contents of ").append(displayPath).append(" (OSS):\n\n");

		String keyPrefix = getFullKey(path);
		if (StringUtils.hasText(keyPrefix) && !keyPrefix.endsWith("/")) {
			keyPrefix += "/";
		}

		ObjectListing listResult = this.ossClient
				.listObjects(new ListObjectsRequest(this.bucketName).withPrefix(keyPrefix).withDelimiter("/"));

		// Common prefixes are "directories"
		for (String commonPrefix : listResult.getCommonPrefixes()) {
			String name = commonPrefix.substring(keyPrefix.length());
			sb.append("  ").append(name).append("\n");
		}

		// Objects are "files"
		for (OSSObjectSummary summary : listResult.getObjectSummaries()) {
			String name = summary.getKey().substring(keyPrefix.length());
			if (StringUtils.hasText(name)) {
				sb.append("  ").append(name).append(" (").append(summary.getSize()).append(" bytes)\n");
			}
		}

		return sb.toString();
	}

	@Override
	public String readString(String path) throws IOException {
		try (OSSObject ossObject = this.ossClient.getObject(this.bucketName, getFullKey(path));
				InputStream is = ossObject.getObjectContent()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Override
	public List<String> readAllLines(String path) throws IOException {
		try (OSSObject ossObject = this.ossClient.getObject(this.bucketName, getFullKey(path));
				InputStream is = ossObject.getObjectContent();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.toList());
		}
	}

	@Override
	public void writeString(String path, String content) throws IOException {
		byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
		try (InputStream is = new ByteArrayInputStream(bytes)) {
			this.ossClient.putObject(this.bucketName, getFullKey(path), is);
		}
	}

	@Override
	public void delete(String path) throws IOException {
		String key = getFullKey(path);
		if (isDirectory(path)) {
			// Recursive delete
			if (!key.endsWith("/")) {
				key += "/";
			}
			String nextMarker = null;
			do {
				ObjectListing listResult = this.ossClient
						.listObjects(new ListObjectsRequest(this.bucketName).withPrefix(key).withMarker(nextMarker));
				List<String> keysToDelete = listResult.getObjectSummaries()
						.stream()
						.map(OSSObjectSummary::getKey)
						.collect(Collectors.toList());
				if (!keysToDelete.isEmpty()) {
					this.ossClient.deleteObjects(new DeleteObjectsRequest(this.bucketName).withKeys(keysToDelete));
				}
				nextMarker = listResult.getNextMarker();
			} while (nextMarker != null);
		} else {
			this.ossClient.deleteObject(this.bucketName, key);
		}
	}

	@Override
	public void rename(String oldPath, String newPath) throws IOException {
		String sourceKey = getFullKey(oldPath);
		String destKey = getFullKey(newPath);

		if (isDirectory(oldPath)) {
			// OSS doesn't have a direct rename for "directories". Must copy and delete all
			// objects.
			if (!sourceKey.endsWith("/")) {
				sourceKey += "/";
			}
			if (!destKey.endsWith("/")) {
				destKey += "/";
			}

			String nextMarker = null;
			do {
				ObjectListing listResult = this.ossClient.listObjects(
						new ListObjectsRequest(this.bucketName).withPrefix(sourceKey).withMarker(nextMarker));
				for (OSSObjectSummary summary : listResult.getObjectSummaries()) {
					String relativeKey = summary.getKey().substring(sourceKey.length());
					this.ossClient.copyObject(this.bucketName, summary.getKey(), this.bucketName,
							destKey + relativeKey);
					this.ossClient.deleteObject(this.bucketName, summary.getKey());
				}
				nextMarker = listResult.getNextMarker();
			} while (nextMarker != null);
		} else {
			this.ossClient.copyObject(this.bucketName, sourceKey, this.bucketName, destKey);
			this.ossClient.deleteObject(this.bucketName, sourceKey);
		}
	}

	private String getFullKey(String path) {
		if (!StringUtils.hasText(path) || path.equals("/")) {
			return this.prefix;
		}
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		return this.prefix + path;
	}

}
