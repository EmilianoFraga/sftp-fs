module com.github.robtimus.filesystems.sftp {
    requires com.github.robtimus.filesystems;
    requires transitive jsch;
    requires static org.slf4j;

    exports com.github.robtimus.filesystems.sftp;
}
