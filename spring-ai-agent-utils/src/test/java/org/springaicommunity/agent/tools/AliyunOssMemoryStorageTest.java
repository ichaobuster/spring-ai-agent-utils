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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AliyunOssMemoryStorage}.
 *
 * @author ichaobuster
 */
@DisplayName("AliyunOssMemoryStorage Tests")
@ExtendWith(MockitoExtension.class)
class AliyunOssMemoryStorageTest {

	@Mock
	private OSS ossClient;

	private AliyunOssMemoryStorage storage;

	private final String bucketName = "test-bucket";

	private final String prefix = "memories/";

	@BeforeEach
	void setUp() {
		storage = new AliyunOssMemoryStorage(ossClient, bucketName, prefix);
	}

	@Test
	@DisplayName("exists() delegates to ossClient")
	void exists() {
		when(ossClient.doesObjectExist(bucketName, prefix + "test.md")).thenReturn(true);
		assertThat(storage.exists("test.md")).isTrue();
	}

	@Test
	@DisplayName("readString() reads object content")
	void readString() throws IOException {
		OSSObject ossObject = new OSSObject();
		ossObject.setObjectContent(new ByteArrayInputStream("hello oss".getBytes(StandardCharsets.UTF_8)));
		when(ossClient.getObject(eq(bucketName), eq(prefix + "test.md"))).thenReturn(ossObject);

		String content = storage.readString("test.md");
		assertThat(content).isEqualTo("hello oss");
	}

	@Test
	@DisplayName("writeString() puts object")
	void writeString() throws IOException {
		storage.writeString("new.md", "content");
		verify(ossClient).putObject(eq(bucketName), eq(prefix + "new.md"), any(ByteArrayInputStream.class));
	}

	@Test
	@DisplayName("listDirectory() lists objects with prefix")
	void listDirectory() throws IOException {
		ObjectListing listing = new ObjectListing();
		OSSObjectSummary summary = new OSSObjectSummary();
		summary.setKey(prefix + "file.md");
		summary.setSize(100L);
		listing.setObjectSummaries(Collections.singletonList(summary));
		listing.setCommonPrefixes(Collections.singletonList(prefix + "subdir/"));

		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(listing);

		String result = storage.listDirectory("");
		assertThat(result).contains("file.md (100 bytes)").contains("subdir/");
	}

	@Test
	@DisplayName("delete() for a file delegates to ossClient")
	void deleteFile() throws IOException {
		// Mock isDirectory to return false for a file
		ObjectListing listing = new ObjectListing();
		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(listing);

		storage.delete("test.md");
		verify(ossClient).deleteObject(bucketName, prefix + "test.md");
	}

	@Test
	@DisplayName("prefix variants in constructor")
	void constructorPrefix() {
		var s1 = new AliyunOssMemoryStorage(ossClient, bucketName, null);
		assertThat(s1.exists("f")).isFalse();
		verify(ossClient).doesObjectExist(bucketName, "f");

		var s2 = new AliyunOssMemoryStorage(ossClient, bucketName, "root");
		s2.exists("f");
		verify(ossClient).doesObjectExist(bucketName, "root/f");
	}

	@Test
	@DisplayName("isDirectory() logic")
	void isDirectory() {
		ObjectListing listing = new ObjectListing();
		OSSObjectSummary summary = new OSSObjectSummary();
		summary.setKey(prefix + "sub/file.md");
		listing.getObjectSummaries().add(summary);

		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(listing);

		assertThat(storage.isDirectory("sub")).isTrue();

		ObjectListing emptyListing = new ObjectListing();
		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(emptyListing);
		assertThat(storage.isDirectory("empty")).isFalse();
	}

	@Test
	@DisplayName("delete() for a directory")
	void deleteDirectory() throws IOException {
		// Mock isDirectory to return true
		ObjectListing listing = new ObjectListing();
		OSSObjectSummary s1 = new OSSObjectSummary();
		s1.setKey(prefix + "dir/f1.md");
		listing.getObjectSummaries().add(s1);

		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(listing);

		storage.delete("dir");

		verify(ossClient).deleteObjects(any(com.aliyun.oss.model.DeleteObjectsRequest.class));
	}

	@Test
	@DisplayName("rename() for a directory")
	void renameDirectory() throws IOException {
		ObjectListing listing = new ObjectListing();
		OSSObjectSummary s1 = new OSSObjectSummary();
		s1.setKey(prefix + "olddir/f1.md");
		listing.getObjectSummaries().add(s1);

		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(listing);

		storage.rename("olddir", "newdir");

		verify(ossClient).copyObject(eq(bucketName), eq(prefix + "olddir/f1.md"), eq(bucketName),
				eq(prefix + "newdir/f1.md"));
		verify(ossClient).deleteObject(bucketName, prefix + "olddir/f1.md");
	}

	@Test
	@DisplayName("listDirectory() with various path inputs")
	void listDirectoryPaths() throws IOException {
		ObjectListing listing = new ObjectListing();
		when(ossClient.listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class))).thenReturn(listing);

		storage.listDirectory("/");
		storage.listDirectory("sub");
		verify(ossClient, times(2)).listObjects(any(com.aliyun.oss.model.ListObjectsRequest.class));
	}

	@Test
	@DisplayName("readAllLines() delegates correctly")
	void readAllLines() throws IOException {
		OSSObject ossObject = new OSSObject();
		ossObject.setObjectContent(new ByteArrayInputStream("L1\nL2".getBytes(StandardCharsets.UTF_8)));
		when(ossClient.getObject(eq(bucketName), eq(prefix + "test.md"))).thenReturn(ossObject);

		List<String> lines = storage.readAllLines("test.md");
		assertThat(lines).containsExactly("L1", "L2");
	}

}
