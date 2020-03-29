/*
 * SFTPFileSystemProviderTest.java
 * Copyright 2016 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.filesystems.sftp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import com.github.robtimus.filesystems.URISupport;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({ "nls", "javadoc" })
public class SFTPFileSystemProviderTest extends AbstractSFTPFileSystemTest {

    // support for Paths and Files

    @Test
    public void testPathsAndFilesSupport() throws IOException {

        try (SFTPFileSystem fs = newFileSystem(createEnv())) {
            Path path = Paths.get(URI.create(getBaseUrl() + "/foo"));
            assertThat(path, instanceOf(SFTPPath.class));
            // as required by Paths.get
            assertEquals(path, path.toAbsolutePath());

            // the file does not exist yet
            assertFalse(Files.exists(path));

            Files.createFile(path);
            try {
                // the file now exists
                assertTrue(Files.exists(path));

                byte[] content = new byte[1024];
                new Random().nextBytes(content);
                try (OutputStream output = Files.newOutputStream(path)) {
                    output.write(content);
                }

                // check the file directly
                Path file = getFile("/foo");
                assertArrayEquals(content, getContents(file));

            } finally {

                Files.delete(path);
                assertFalse(Files.exists(path));

                assertFalse(Files.exists(getPath("/foo")));
            }
        }
    }

    @Test(expected = FileSystemNotFoundException.class)
    public void testPathsAndFilesSupportFileSystemNotFound() {
        Paths.get(URI.create("sftp://sftp.github.com/"));
    }

    // SFTPFileSystemProvider.removeFileSystem

    @Test(expected = FileSystemNotFoundException.class)
    public void testRemoveFileSystem() throws IOException {
        addDirectory("/foo/bar");

        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        URI uri;
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            SFTPPath path = new SFTPPath(fs, "/foo/bar");

            uri = path.toUri();

            assertFalse(provider.isHidden(path));
        }
        provider.getPath(uri);
    }

    // SFTPFileSystemProvider.getPath

    @Test
    public void testGetPath() throws IOException {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("/", "/");
        inputs.put("foo", "/home/foo");
        inputs.put("/foo", "/foo");
        inputs.put("foo/bar", "/home/foo/bar");
        inputs.put("/foo/bar", "/foo/bar");

        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            for (Map.Entry<String, String> entry : inputs.entrySet()) {
                URI uri = fs.getPath(entry.getKey()).toUri();
                Path path = provider.getPath(uri);
                assertThat(path, instanceOf(SFTPPath.class));
                assertEquals(entry.getValue(), ((SFTPPath) path).path());
            }
            for (Map.Entry<String, String> entry : inputs.entrySet()) {
                URI uri = fs.getPath(entry.getKey()).toUri();
                uri = URISupport.create(uri.getScheme().toUpperCase(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), null, null);
                Path path = provider.getPath(uri);
                assertThat(path, instanceOf(SFTPPath.class));
                assertEquals(entry.getValue(), ((SFTPPath) path).path());
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPathNoScheme() {
        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        provider.getPath(URI.create("/foo/bar"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPathInvalidScheme() {
        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        provider.getPath(URI.create("https://www.github.com/"));
    }

    @Test(expected = FileSystemNotFoundException.class)
    public void testGetPathFileSystemNotFound() {
        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        provider.getPath(URI.create("sftp://sftp.github.com/"));
    }

    // SFTPFileSystemProvider.getFileAttributeView

    @Test
    public void testGetFileAttributeViewBasic() throws IOException {

        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            SFTPPath path = new SFTPPath(fs, "/foo/bar");

            BasicFileAttributeView view = fs.provider().getFileAttributeView(path, BasicFileAttributeView.class);
            assertNotNull(view);
            assertEquals("basic", view.name());
        }
    }

    @Test
    public void testGetFileAttributeViewPosix() throws IOException {

        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            SFTPPath path = new SFTPPath(fs, "/foo/bar");

            PosixFileAttributeView view = fs.provider().getFileAttributeView(path, PosixFileAttributeView.class);
            assertNotNull(view);
            assertEquals("posix", view.name());
        }
    }

    @Test
    public void testGetFileAttributeViewReadAttributes() throws IOException {
        addDirectory("/foo/bar");

        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            SFTPPath path = new SFTPPath(fs, "/foo/bar");

            BasicFileAttributeView view = fs.provider().getFileAttributeView(path, BasicFileAttributeView.class);
            assertNotNull(view);

            BasicFileAttributes attributes = view.readAttributes();
            assertTrue(attributes.isDirectory());
        }
    }

    // SFTPFileSystemProvider.keepAlive

    @Test
    public void testKeepAliveWithFTPFileSystem() throws IOException {
        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            SFTPFileSystemProvider.keepAlive(fs);
        }
    }

    @Test(expected = ProviderMismatchException.class)
    public void testKeepAliveWithNonFTPFileSystem() throws IOException {
        @SuppressWarnings("resource")
        FileSystem defaultFileSystem = FileSystems.getDefault();
        SFTPFileSystemProvider.keepAlive(defaultFileSystem);
    }

    @Test(expected = ProviderMismatchException.class)
    public void testKeepAliveWithNullFTPFileSystem() throws IOException {
        SFTPFileSystemProvider.keepAlive(null);
    }

    // SFTPFileSystemProvider.createDirectory through Files.createDirectories

    @Test
    public void testCreateDirectories() throws IOException {
        addDirectory("/foo/bar");

        SFTPFileSystemProvider provider = new SFTPFileSystemProvider();
        try (SFTPFileSystem fs = newFileSystem(provider, createEnv())) {
            SFTPPath path = new SFTPPath(fs, "/foo/bar");
            Files.createDirectories(path);
        }

        assertTrue(Files.exists(getPath("/foo/bar")));
    }

    private SFTPFileSystem newFileSystem(Map<String, ?> env) throws IOException {
        return (SFTPFileSystem) FileSystems.newFileSystem(getURI(), env);
    }

    private SFTPFileSystem newFileSystem(SFTPFileSystemProvider provider, Map<String, ?> env) throws IOException {
        return (SFTPFileSystem) provider.newFileSystem(getURI(), env);
    }
}
