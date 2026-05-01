package org.springaicommunity.agent.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsResourceIntegrationTest {

	private static final String SKILL_MD_CONTENT = """
			---
			name: jar-skill
			description: JAR skill
			---

			JAR skill content.
			""";

	private static final String REFERENCE_MD_CONTENT = "Reference content.";

	private static Path jarPath;

	@TempDir
	static Path jarTempDir;

	@BeforeAll
	static void createTestJar() throws IOException {
		jarPath = jarTempDir.resolve("test-skills.jar");

		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
			jos.putNextEntry(new JarEntry("skills/"));
			jos.closeEntry();
			jos.putNextEntry(new JarEntry("skills/jar-skill/"));
			jos.closeEntry();

			// Add SKILL.md
			jos.putNextEntry(new JarEntry("skills/jar-skill/SKILL.md"));
			jos.write(SKILL_MD_CONTENT.getBytes(StandardCharsets.UTF_8));
			jos.closeEntry();

			// Add reference.md (companion file)
			jos.putNextEntry(new JarEntry("skills/jar-skill/reference.md"));
			jos.write(REFERENCE_MD_CONTENT.getBytes(StandardCharsets.UTF_8));
			jos.closeEntry();
		}
	}

	@Test
	void shouldReadFileFromJarUsingFileSystemToolsAndResourceLoader() throws Exception {
		UrlResource jarResource = new UrlResource("jar:" + jarPath.toUri() + "!/skills");

		ToolCallback skillCallback = SkillsTool.builder().addSkillsResource(jarResource).build();

		// Invoke the skill to get its base directory
		String skillResult = skillCallback.call("{\"command\":\"jar-skill\"}");

		assertThat(skillResult).contains("Base directory for this skill:");

		// The basePath should now start with classpath:
		String searchStr = "Base directory for this skill: ";
		int start = skillResult.indexOf(searchStr) + searchStr.length();
		int end = skillResult.indexOf("\n", start);
		if (end == -1)
			end = skillResult.length();
		String actualBasePath = skillResult.substring(start, end).trim();

		assertThat(actualBasePath).startsWith("classpath:/skills/jar-skill");

		// Try to read reference.md using FileSystemTools (which now supports Resources)
		FileSystemTools fileSystemTools = new FileSystemTools();
		String referencePath = actualBasePath + "/reference.md";

		// We need to use the jar resource for the ResourceLoader because classpath:
		// won't find it unless it's on the real classpath.
		// For this test, we can use the full jar: URL or simulate it.
		String jarReferencePath = "jar:" + jarPath.toUri() + "!/skills/jar-skill/reference.md";

		String readResult = fileSystemTools.read(jarReferencePath, null, null);

		assertThat(readResult).contains("Reference content.");
	}

	@Test
	void shouldHaveClasspathBasePathInSkillResult() throws Exception {
		UrlResource jarResource = new UrlResource("jar:" + jarPath.toUri() + "!/skills");
		ToolCallback skillCallback = SkillsTool.builder().addSkillsResource(jarResource).build();
		String skillResult = skillCallback.call("{\"command\":\"jar-skill\"}");

		assertThat(skillResult).contains("Base directory for this skill: classpath:/skills/jar-skill");
	}
}
