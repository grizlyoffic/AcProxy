// app/src/main/aidl/com/acproxy/IFileService.aidl
package com.acproxy;

interface IFileService {
    boolean createFile(String path, String content);
    boolean deleteFile(String path);
    boolean fileExists(String path);
}