/*
 * SFTPEnvironment.java
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

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.github.robtimus.filesystems.FileSystemProviderSupport;
import com.github.robtimus.filesystems.Messages;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserInfo;

/**
 * A utility class to set up environments that can be used in the {@link FileSystemProvider#newFileSystem(URI, Map)} and
 * {@link FileSystemProvider#newFileSystem(Path, Map)} methods of {@link SFTPFileSystemProvider}.
 *
 * @author Rob Spoor
 */
public class SFTPEnvironment implements Map<String, Object>, Cloneable {

    // session support

    private static final String USERNAME = "username"; //$NON-NLS-1$

    // connect support

    private static final String CONNECT_TIMEOUT = "connectTimeout"; //$NON-NLS-1$

    // JSch
    private static final String IDENTITY_REPOSITORY = "identityRepository"; //$NON-NLS-1$
    private static final String IDENTITIES = "identities"; //$NON-NLS-1$
    private static final String HOST_KEY_REPOSITORY = "hostKeyRepository"; //$NON-NLS-1$
    private static final String KNOWN_HOSTS = "knownHosts"; //$NON-NLS-1$

    // Session

    private static final String PROXY = "proxy"; //$NON-NLS-1$
    private static final String USER_INFO = "userInfo"; //$NON-NLS-1$
    private static final String PASSWORD = "password"; //$NON-NLS-1$
    private static final String CONFIG = "config"; //$NON-NLS-1$
    private static final String SOCKET_FACTORY = "socketFactory"; //$NON-NLS-1$
    // timeOut should have been timeout, but that's a breaking change...
    private static final String TIMEOUT = "timeOut"; //$NON-NLS-1$
    private static final String CLIENT_VERSION = "clientVersion"; //$NON-NLS-1$
    private static final String HOST_KEY_ALIAS = "hostKeyAlias"; //$NON-NLS-1$
    private static final String SERVER_ALIVE_INTERVAL = "serverAliveInterval"; //$NON-NLS-1$
    private static final String SERVER_ALIVE_COUNT_MAX = "serverAliveCountMax"; //$NON-NLS-1$
    // don't support port forwarding, X11

    // ChannelSession

    private static final String AGENT_FORWARDING = "agentForwarding"; //$NON-NLS-1$
    // don't support X11 forwarding, PTY or TTY

    // ChannelSftp

    private static final String FILENAME_ENCODING = "filenameEncoding"; //$NON-NLS-1$

    // SFTP file system support

    private static final String DEFAULT_DIR = "defaultDir"; //$NON-NLS-1$
    private static final int DEFAULT_CLIENT_CONNECTION_COUNT = 5;
    private static final long DEFAULT_CLIENT_CONNECTION_WAIT_TIMEOUT = 0;
    private static final String CLIENT_CONNECTION_COUNT = "clientConnectionCount"; //$NON-NLS-1$
    private static final String CLIENT_CONNECTION_WAIT_TIMEOUT = "clientConnectionWaitTimeout"; //$NON-NLS-1$
    private static final String FILE_SYSTEM_EXCEPTION_FACTORY = "fileSystemExceptionFactory"; //$NON-NLS-1$

    private Map<String, Object> map;

    /**
     * Creates a new SFTP environment.
     */
    public SFTPEnvironment() {
        map = new HashMap<>();
    }

    /**
     * Creates a new SFTP environment.
     *
     * @param map The map to wrap.
     */
    public SFTPEnvironment(Map<String, Object> map) {
        this.map = Objects.requireNonNull(map);
    }

    @SuppressWarnings("unchecked")
    static SFTPEnvironment wrap(Map<String, ?> map) {
        if (map instanceof SFTPEnvironment) {
            return (SFTPEnvironment) map;
        }
        return new SFTPEnvironment((Map<String, Object>) map);
    }

    // session support

    /**
     * Stores the username to use.
     *
     * @param username The username to use.
     * @return This object.
     */
    public SFTPEnvironment withUsername(String username) {
        put(USERNAME, username);
        return this;
    }

    // connect support

    /**
     * Stores the connection timeout to use.
     *
     * @param timeout The connection timeout in milliseconds.
     * @return This object.
     */
    public SFTPEnvironment withConnectTimeout(int timeout) {
        put(CONNECT_TIMEOUT, timeout);
        return this;
    }

    // Session

    /**
     * Stores the proxy to use.
     *
     * @param proxy The proxy to use.
     * @return This object.
     */
    public SFTPEnvironment withProxy(Proxy proxy) {
        put(PROXY, proxy);
        return this;
    }

    /**
     * Stores the user info to use.
     *
     * @param userInfo The user info to use.
     * @return This object.
     */
    public SFTPEnvironment withUserInfo(UserInfo userInfo) {
        put(USER_INFO, userInfo);
        return this;
    }

    /**
     * Stores the password to use.
     *
     * @param password The password to use.
     * @return This object.
     * @since 1.1
     */
    public SFTPEnvironment withPassword(char[] password) {
        put(PASSWORD, password);
        return this;
    }

    /**
     * Stores configuration options to use. This method will add not clear any previously set options, but only add new ones.
     *
     * @param config The configuration options to use.
     * @return This object.
     * @throws NullPointerException if the given properties object is {@code null}.
     * @see #withConfig(String, String)
     */
    public SFTPEnvironment withConfig(Properties config) {
        getConfig().putAll(config);
        return this;
    }

    /**
     * Stores a configuration option to use. This method will add not clear any previously set options, but only add new ones.
     *
     * @param key The configuration key.
     * @param value The configuration value.
     * @return This object.
     * @throws NullPointerException if the given key or value is {@code null}.
     * @see #withConfig(Properties)
     */
    public SFTPEnvironment withConfig(String key, String value) {
        getConfig().setProperty(key, value);
        return this;
    }

    private Properties getConfig() {
        Properties config = FileSystemProviderSupport.getValue(this, CONFIG, Properties.class, null);
        if (config == null) {
            config = new Properties();
            put(CONFIG, config);
        }
        return config;
    }

    /**
     * Stores the socket factory to use.
     *
     * @param factory The socket factory to use.
     * @return This object.
     */
    public SFTPEnvironment withSocketFactory(SocketFactory factory) {
        put(SOCKET_FACTORY, factory);
        return this;
    }

    /**
     * Stores the timeout.
     *
     * @param timeout The timeout in milliseconds.
     * @return This object.
     * @see Socket#setSoTimeout(int)
     */
    public SFTPEnvironment withTimeout(int timeout) {
        put(TIMEOUT, timeout);
        return this;
    }

    /**
     * Stores the client version to use.
     *
     * @param version The client version.
     * @return This object.
     */
    public SFTPEnvironment withClientVersion(String version) {
        put(CLIENT_VERSION, version);
        return this;
    }

    /**
     * Stores the host key alias to use.
     *
     * @param alias The host key alias.
     * @return This object.
     */
    public SFTPEnvironment withHostKeyAlias(String alias) {
        put(HOST_KEY_ALIAS, alias);
        return this;
    }

    /**
     * Stores the server alive interval to use.
     *
     * @param interval The server alive interval in milliseconds.
     * @return This object.
     */
    public SFTPEnvironment withServerAliveInterval(int interval) {
        put(SERVER_ALIVE_INTERVAL, interval);
        return this;
    }

    /**
     * Stores the maximum number of server alive messages that can be sent without any reply before disconnecting.
     *
     * @param count The maximum number of server alive messages.
     * @return This object.
     */
    public SFTPEnvironment withServerAliveCountMax(int count) {
        put(SERVER_ALIVE_COUNT_MAX, count);
        return this;
    }

    /**
     * Stores the identity repository to use.
     *
     * @param repository The identity repository to use.
     * @return This object.
     */
    public SFTPEnvironment withIdentityRepository(IdentityRepository repository) {
        put(IDENTITY_REPOSITORY, repository);
        return this;
    }

    /**
     * Stores an identity to use. This method will add not clear any previously set identities, but only add new ones.
     *
     * @param identity The identity to use.
     * @return This object.
     * @throws NullPointerException If the given identity is {@code null}.
     * @see #withIdentities(Identity...)
     * @see #withIdentities(Collection)
     * @since 1.2
     */
    public SFTPEnvironment withIdentity(Identity identity) {
        Objects.requireNonNull(identity);
        getIdentities().add(identity);
        return this;
    }

    /**
     * Stores several identity to use. This method will add not clear any previously set identities, but only add new ones.
     *
     * @param identities The identities to use.
     * @return This object.
     * @throws NullPointerException If any of the given identity is {@code null}.
     * @see #withIdentity(Identity)
     * @see #withIdentities(Collection)
     * @since 1.2
     */
    public SFTPEnvironment withIdentities(Identity... identities) {
        Collection<Identity> existingIdentities = getIdentities();
        for (Identity identity : identities) {
            Objects.requireNonNull(identity);
            existingIdentities.add(identity);
        }
        return this;
    }

    /**
     * Stores several identity to use. This method will add not clear any previously set identities, but only add new ones.
     *
     * @param identities The identities to use.
     * @return This object.
     * @throws NullPointerException If any of the given identity is {@code null}.
     * @see #withIdentity(Identity)
     * @see #withIdentities(Identity...)
     * @since 1.2
     */
    public SFTPEnvironment withIdentities(Collection<Identity> identities) {
        Collection<Identity> existingIdentities = getIdentities();
        for (Identity identity : identities) {
            Objects.requireNonNull(identity);
            existingIdentities.add(identity);
        }
        return this;
    }

    private Collection<Identity> getIdentities() {
        @SuppressWarnings("unchecked")
        List<Identity> identities = FileSystemProviderSupport.getValue(this, IDENTITIES, List.class, null);
        if (identities == null) {
            identities = new ArrayList<>();
            put(IDENTITIES, identities);
        }
        return identities;
    }

    /**
     * Stores the host key repository to use.
     *
     * @param repository The host key repository to use.
     * @return This object.
     */
    public SFTPEnvironment withHostKeyRepository(HostKeyRepository repository) {
        put(HOST_KEY_REPOSITORY, repository);
        return this;
    }

    /**
     * Stores the known hosts file to use.
     * Note that the known hosts file is ignored if a {@link HostKeyRepository} is set with a non-{@code null} value.
     *
     * @param knownHosts The known hosts file to use.
     * @return This object.
     * @throws NullPointerException If the given file is {@code null}.
     * @see #withHostKeyRepository(HostKeyRepository)
     * @since 1.2
     */
    public SFTPEnvironment withKnownHosts(File knownHosts) {
        Objects.requireNonNull(knownHosts);
        put(KNOWN_HOSTS, knownHosts);
        return this;
    }

    /**
     * Stores whether or not agent forwarding should be enabled.
     *
     * @param agentForwarding {@code true} to enable strict agent forwarding, or {@code false} to disable it.
     * @return This object.
     */
    public SFTPEnvironment withAgentForwarding(boolean agentForwarding) {
        put(AGENT_FORWARDING, agentForwarding);
        return this;
    }

    /**
     * Stores the filename encoding to use.
     *
     * @param encoding The filename encoding to use.
     * @return This object.
     */
    public SFTPEnvironment withFilenameEncoding(String encoding) {
        put(FILENAME_ENCODING, encoding);
        return this;
    }

    // SFTP file system support

    /**
     * Stores the default directory to use.
     * If it exists, this will be the directory that relative paths are resolved to.
     *
     * @param pathname The default directory to use.
     * @return This object.
     */
    public SFTPEnvironment withDefaultDirectory(String pathname) {
        put(DEFAULT_DIR, pathname);
        return this;
    }

    /**
     * Stores the number of client connections to use. This value influences the number of concurrent threads that can access an SFTP file system.
     *
     * @param count The number of client connection to use.
     * @return This object.
     */
    public SFTPEnvironment withClientConnectionCount(int count) {
        put(CLIENT_CONNECTION_COUNT, count);
        return this;
    }

    /**
     * Stores the wait timeout to use for retrieving client connection from the connection pool.
     * <p>
     * If the timeout is not larger than {@code 0}, the SFTP file system waits indefinitely until a client connection becomes available.
     *
     * @param timeout The timeout in milliseconds.
     * @return This object.
     * @see #withClientConnectionWaitTimeout(long, TimeUnit)
     * @since 1.3
     */
    public SFTPEnvironment withClientConnectionWaitTimeout(long timeout) {
        put(CLIENT_CONNECTION_WAIT_TIMEOUT, timeout);
        return this;
    }

    /**
     * Stores the wait timeout to use for retrieving client connections from the connection pool.
     * <p>
     * If the timeout is not larger than {@code 0}, the SFTP file system waits indefinitely until a client connection becomes available.
     *
     * @param duration The timeout duration.
     * @param unit The timeout unit.
     * @return This object.
     * @throws NullPointerException If the timeout unit is {@code null}.
     * @see #withClientConnectionWaitTimeout(long)
     * @since 1.3
     */
    public SFTPEnvironment withClientConnectionWaitTimeout(long duration, TimeUnit unit) {
        return withClientConnectionWaitTimeout(TimeUnit.MILLISECONDS.convert(duration, unit));
    }

    /**
     * Stores the file system exception factory to use.
     *
     * @param factory The file system exception factory to use.
     * @return This object.
     */
    public SFTPEnvironment withFileSystemExceptionFactory(FileSystemExceptionFactory factory) {
        put(FILE_SYSTEM_EXCEPTION_FACTORY, factory);
        return this;
    }

    String getUsername() {
        return FileSystemProviderSupport.getValue(this, USERNAME, String.class, null);
    }

    int getClientConnectionCount() {
        int count = FileSystemProviderSupport.getIntValue(this, CLIENT_CONNECTION_COUNT, DEFAULT_CLIENT_CONNECTION_COUNT);
        return Math.max(1, count);
    }

    long getClientConnectionWaitTimeout() {
        long timeout = FileSystemProviderSupport.getLongValue(this, CLIENT_CONNECTION_WAIT_TIMEOUT, DEFAULT_CLIENT_CONNECTION_WAIT_TIMEOUT);
        return Math.max(0, timeout);
    }

    FileSystemExceptionFactory getExceptionFactory() {
        return FileSystemProviderSupport.getValue(this, FILE_SYSTEM_EXCEPTION_FACTORY, FileSystemExceptionFactory.class,
                DefaultFileSystemExceptionFactory.INSTANCE);
    }

    JSch createJSch() throws IOException {
        JSch jsch = new JSch();
        initialize(jsch);
        return jsch;
    }

    void initialize(JSch jsch) throws IOException {
        if (containsKey(IDENTITY_REPOSITORY)) {
            IdentityRepository identityRepository = FileSystemProviderSupport.getValue(this, IDENTITY_REPOSITORY, IdentityRepository.class, null);
            jsch.setIdentityRepository(identityRepository);
        }
        if (containsKey(IDENTITIES)) {
            Collection<?> identities = FileSystemProviderSupport.getValue(this, IDENTITIES, Collection.class);
            for (Object o : identities) {
                if (o instanceof Identity) {
                    Identity identity = (Identity) o;
                    try {
                        identity.addIdentity(jsch);
                    } catch (JSchException e) {
                        throw asFileSystemException(e);
                    }
                } else {
                    throw Messages.fileSystemProvider().env().invalidProperty(IDENTITIES, identities);
                }
            }
        }

        if (containsKey(HOST_KEY_REPOSITORY)) {
            HostKeyRepository hostKeyRepository = FileSystemProviderSupport.getValue(this, HOST_KEY_REPOSITORY, HostKeyRepository.class, null);
            jsch.setHostKeyRepository(hostKeyRepository);
        }
        if (containsKey(KNOWN_HOSTS)) {
            File knownHosts = FileSystemProviderSupport.getValue(this, KNOWN_HOSTS, File.class);
            try {
                jsch.setKnownHosts(knownHosts.getAbsolutePath());
            } catch (JSchException e) {
                throw asFileSystemException(e);
            }
        }
    }

    ChannelSftp openChannel(JSch jsch, String hostname, int port) throws IOException {
        Session session = getSession(jsch, hostname, port);
        try {
            initialize(session);
            ChannelSftp channel = connect(session);
            try {
                initializePreConnect(channel);
                connect(channel);
                initializePostConnect(channel);
                verifyConnection(channel);
                return channel;
            } catch (IOException e) {
                channel.disconnect();
                throw e;
            }
        } catch (IOException e) {
            session.disconnect();
            throw e;
        }
    }

    Session getSession(JSch jsch, String hostname, int port) throws IOException {
        String username = getUsername();

        try {
            return jsch.getSession(username, hostname, port == -1 ? 22 : port);
        } catch (JSchException e) {
            throw asFileSystemException(e);
        }
    }

    void initialize(Session session) throws IOException {
        if (containsKey(PROXY)) {
            Proxy proxy = FileSystemProviderSupport.getValue(this, PROXY, Proxy.class, null);
            session.setProxy(proxy);
        }

        if (containsKey(USER_INFO)) {
            UserInfo userInfo = FileSystemProviderSupport.getValue(this, USER_INFO, UserInfo.class, null);
            session.setUserInfo(userInfo);
        }

        if (containsKey(PASSWORD)) {
            char[] password = FileSystemProviderSupport.getValue(this, PASSWORD, char[].class, null);
            session.setPassword(password == null ? null : new String(password));
        }

        if (containsKey(CONFIG)) {
            Properties config = FileSystemProviderSupport.getValue(this, CONFIG, Properties.class, null);
            session.setConfig(config);
        }

        if (containsKey(SOCKET_FACTORY)) {
            SocketFactory socketFactory = FileSystemProviderSupport.getValue(this, SOCKET_FACTORY, SocketFactory.class, null);
            session.setSocketFactory(socketFactory);
        }

        if (containsKey(TIMEOUT)) {
            int timeout = FileSystemProviderSupport.getIntValue(this, TIMEOUT);
            try {
                session.setTimeout(timeout);
            } catch (JSchException e) {
                throw asFileSystemException(e);
            }
        }

        if (containsKey(CLIENT_VERSION)) {
            String clientVersion = FileSystemProviderSupport.getValue(this, CLIENT_VERSION, String.class, null);
            session.setClientVersion(clientVersion);
        }

        if (containsKey(HOST_KEY_ALIAS)) {
            String hostKeyAlias = FileSystemProviderSupport.getValue(this, HOST_KEY_ALIAS, String.class, null);
            session.setHostKeyAlias(hostKeyAlias);
        }

        if (containsKey(SERVER_ALIVE_INTERVAL)) {
            int interval = FileSystemProviderSupport.getIntValue(this, SERVER_ALIVE_INTERVAL);
            try {
                session.setServerAliveInterval(interval);
            } catch (JSchException e) {
                throw asFileSystemException(e);
            }
        }
        if (containsKey(SERVER_ALIVE_COUNT_MAX)) {
            int count = FileSystemProviderSupport.getIntValue(this, SERVER_ALIVE_COUNT_MAX);
            session.setServerAliveCountMax(count);
        }
    }

    ChannelSftp connect(Session session) throws IOException {
        try {
            if (containsKey(CONNECT_TIMEOUT)) {
                int connectTimeout = FileSystemProviderSupport.getIntValue(this, CONNECT_TIMEOUT);
                session.connect(connectTimeout);
            } else {
                session.connect();
            }

            return (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
        } catch (JSchException e) {
            throw asFileSystemException(e);
        }
    }

    void initializePreConnect(ChannelSftp channel) throws IOException {
        if (containsKey(AGENT_FORWARDING)) {
            boolean forwarding = FileSystemProviderSupport.getBooleanValue(this, AGENT_FORWARDING);
            channel.setAgentForwarding(forwarding);
        }

        if (containsKey(FILENAME_ENCODING)) {
            String filenameEncoding = FileSystemProviderSupport.getValue(this, FILENAME_ENCODING, String.class, null);
            try {
                Charset charset = filenameEncoding != null ? Charset.forName(filenameEncoding) : null;
                channel.setFilenameEncoding(charset);
            } catch (UnsupportedCharsetException e) {
                throw asFileSystemException(e);
            }
        }
    }

    void connect(ChannelSftp channel) throws IOException {
        try {
            if (containsKey(CONNECT_TIMEOUT)) {
                int connectTimeout = FileSystemProviderSupport.getIntValue(this, CONNECT_TIMEOUT);
                channel.connect(connectTimeout);
            } else {
                channel.connect();
            }
        } catch (JSchException e) {
            throw asFileSystemException(e);
        }
    }

    void initializePostConnect(ChannelSftp channel) throws IOException {
        String defaultDir = FileSystemProviderSupport.getValue(this, DEFAULT_DIR, String.class, null);
        if (defaultDir != null) {
            try {
                channel.cd(defaultDir);
            } catch (SftpException e) {
                throw getExceptionFactory().createChangeWorkingDirectoryException(defaultDir, e);
            }
        }
    }

    void verifyConnection(ChannelSftp channel) throws IOException {
        try {
            channel.pwd();
        } catch (SftpException e) {
            throw asFileSystemException(e);
        }
    }

    FileSystemException asFileSystemException(Exception e) throws FileSystemException {
        if (e instanceof FileSystemException) {
            throw (FileSystemException) e;
        }
        FileSystemException exception = new FileSystemException(null, null, e.getMessage());
        exception.initCause(e);
        throw exception;
    }

    // Map / Object

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return map.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return map.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<Object> values() {
        return map.values();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return map.equals(o);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public SFTPEnvironment clone() {
        try {
            SFTPEnvironment clone = (SFTPEnvironment) super.clone();
            clone.map = new HashMap<>(map);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
