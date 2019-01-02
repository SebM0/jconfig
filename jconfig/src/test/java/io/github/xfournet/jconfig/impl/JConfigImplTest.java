package io.github.xfournet.jconfig.impl;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import io.github.xfournet.jconfig.JConfig;
import io.github.xfournet.jconfig.Util;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class JConfigImplTest {

    @DataProvider(name = "applyAndDiffScenarios")
    public Object[][] providesApplyAndDiffScenarios() {
        return new Object[][]{ //
                {"scenario_1", "root_1",//
                        asList("conf/jvm.conf", "conf/log4j.properties", "conf/platform.properties", "conf/unmodified.properties", "lib/plugin0.jar",
                               "var/data/default0.hash"), //
                        asList("conf/jvm.conf", "conf/log4j.properties", "conf/platform.properties", "conf/unmodified.properties", "lib/plugin.jar",
                               "var/data/default.hash")}, //
        };
    }

    @Test(dataProvider = "applyAndDiffScenarios")
    public void testApplyAndDiff(String scenario, String sourcePrefix, List<String> sourceNames, List<String> resultNames) throws Exception {
        Path root = Paths.get("jconfig/" + scenario);
        Util.ensureCleanDirectory(root);

        Path applyFile = deploy(root, scenario, "jconfig-apply.ini");
        Path expectedDiffFile = deploy(root, scenario, "jconfig-diff.ini");

        Path testDir = root.resolve("test");
        for (String sourceName : sourceNames) {
            deploy(testDir, sourcePrefix, sourceName);
        }

        Path expectedDir = root.resolve("expected");
        for (String resultName : resultNames) {
            deploy(expectedDir, scenario + "/expected", resultName);
        }

        Path diffFile = root.resolve("diff.ini");
        JConfig jConfig = JConfig.newDefaultJConfig(expectedDir);
        jConfig.diff(testDir, diffFile);

        assertThat(diffFile).hasSameContentAs(expectedDiffFile);

        jConfig = JConfig.newDefaultJConfig(testDir);
        jConfig.apply(applyFile);

        assertSameDirectoryContent(testDir, expectedDir);
    }

    @DataProvider(name = "setEntries")
    public Object[][] providesSetEntries() {
        return new Object[][]{ //
                {"setentries_1", "root_1/conf", "platform.properties", Arrays.asList("key=abc", "https.port=443")}, //
        };
    }

    @Test(dataProvider = "setEntries")
    public void testSetEntries(String scenario, String sourcePrefix, String name, List<String> entries) throws Exception {
        Path root = Paths.get("jconfig/" + scenario);
        Util.ensureCleanDirectory(root);

        Path testDir = root.resolve("test");
        Path sourceFile = deploy(testDir, sourcePrefix, name);

        Path expectedDir = root.resolve("expected");
        Path expectedFile = deploy(expectedDir, scenario, name);

        JConfig jConfig = JConfig.newDefaultJConfig(testDir);
        jConfig.setEntries(testDir.relativize(sourceFile), entries);

        assertThat(sourceFile).hasSameContentAs(expectedFile);
    }

    @DataProvider(name = "removeEntries")
    public Object[][] providesRemoveEntries() {
        return new Object[][]{ //
                {"removeentries_1", "root_1/conf", "platform.properties", Arrays.asList("dont.exist", "key")}, //
        };
    }

    @Test(dataProvider = "removeEntries")
    public void testRemoveEntries(String scenario, String sourcePrefix, String name, List<String> entries) throws Exception {
        Path root = Paths.get("jconfig/" + scenario);
        Util.ensureCleanDirectory(root);

        Path testDir = root.resolve("test");
        Path sourceFile = deploy(testDir, sourcePrefix, name);

        Path expectedDir = root.resolve("expected");
        Path expectedFile = deploy(expectedDir, scenario, name);

        JConfig jConfig = JConfig.newDefaultJConfig(testDir);
        jConfig.removeEntries(testDir.relativize(sourceFile), entries);

        assertThat(sourceFile).hasSameContentAs(expectedFile);
    }

    @DataProvider(name = "mergeScenarios")
    public Object[][] providesMergeScenarios() {
        return new Object[][]{ //
                {"merge_1", "root_1",//
                        asList("conf/jvm.conf", "conf/log4j.properties", "conf/platform.properties", "conf/unmodified.properties", "lib/plugin0.jar",
                               "var/data/default0.hash"), //
                        asList("conf/jvm.conf", "conf/log4j.properties", "lib/plugin.jar", "var/data/default.hash"), //
                        asList("conf/jvm.conf", "conf/log4j.properties", "conf/platform.properties", "conf/unmodified.properties", "lib/plugin.jar",
                               "lib/plugin0.jar", "var/data/default.hash", "var/data/default0.hash")}, //
        };
    }

    @Test(dataProvider = "mergeScenarios")
    public void testMergeScenarios(String scenario, String sourcePrefix, List<String> sourceNames, List<String> mergeNames, List<String> resultNames)
            throws Exception {
        Path root = Paths.get("jconfig/" + scenario);
        Util.ensureCleanDirectory(root);

        Path mergeDir = root.resolve("merge");
        for (String mergeName : mergeNames) {
            deploy(mergeDir, scenario + "/merge", mergeName);
        }
        Path mergeFile = deploy(root, scenario, "merge.zip");

        Path expectedDir = root.resolve("expected");
        for (String resultName : resultNames) {
            deploy(expectedDir, scenario + "/expected", resultName);
        }

        Path testDir = root.resolve("test");

        for (Path mergeSource : Arrays.asList(mergeDir, mergeFile)) {

            Util.ensureCleanDirectory(testDir);
            for (String sourceName : sourceNames) {
                deploy(testDir, sourcePrefix, sourceName);
            }

            JConfig jConfig = JConfig.newDefaultJConfig(testDir);
            jConfig.merge(mergeSource);

            assertSameDirectoryContent(testDir, expectedDir);
        }
    }

    @DataProvider(name = "mergeFile")
    public Object[][] providesMergeFile() {
        return new Object[][]{ //
                {"mergefile_1", "root_1", "conf/platform.properties", "tomerge.properties", "expected.properties"}, //
        };
    }

    @Test(dataProvider = "mergeFile")
    public void testMergeFile(String scenario, String sourcePrefix, String targetName, String mergeName, String expectedName) throws Exception {
        Path root = Paths.get("jconfig/" + scenario);
        Util.ensureCleanDirectory(root);

        Path testDir = root.resolve("test");
        Path targetFile = deploy(testDir, sourcePrefix, targetName);

        Path expectedFile = deploy(root, scenario, expectedName);

        Path sourceFile = deploy(root, scenario, mergeName);

        JConfig jConfig = JConfig.newDefaultJConfig(testDir);
        jConfig.merge(Paths.get("conf", "platform.properties"), sourceFile);

        assertThat(targetFile).hasSameContentAs(expectedFile);
    }

    private void assertSameDirectoryContent(Path testDir, Path expectedDir) throws IOException {
        Set<Path> validatedTestFiles = new HashSet<>();

        try (Stream<Path> expectedPaths = Files.walk(expectedDir)) {
            expectedPaths.filter(Files::isRegularFile).forEach(expectedFile -> {
                Path relativePath = expectedDir.relativize(expectedFile);
                Path resultFile = testDir.resolve(relativePath);
                assertThat(resultFile).isRegularFile();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (InputStream in = Files.newInputStream(expectedFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        bos.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                assertThat(resultFile).hasBinaryContent(bos.toByteArray());
                validatedTestFiles.add(resultFile);
            });
        }

        try (Stream<Path> resultPaths = Files.walk(testDir)) {
            resultPaths //
                    .filter(Files::isRegularFile) //
                    .filter(resultFile -> !validatedTestFiles.contains(resultFile)) //
                    .forEach(resultFile -> fail("Unexpected file in result: " + resultFile));
        }
    }

    private Path deploy(Path root, String resourcePrefix, String name) throws IOException {
        Path output = root.resolve(name);
        try (InputStream in = JConfigImplTest.class.getResourceAsStream(resourcePrefix + "/" + name)) {
            Files.createDirectories(output.getParent());
            Files.copy(in, output, REPLACE_EXISTING);
        }
        return output;
    }
}
