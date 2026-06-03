package com.acproxy;

import java.io.File;
import java.io.FileWriter;

public class FileService extends IFileService.Stub {
    
    @Override
    public boolean createFile(String path, String content) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (file.exists()) file.delete();
            file.createNewFile();
            FileWriter fw = new FileWriter(file);
            fw.write(content);
            fw.flush();
            fw.close();
            return file.exists() && file.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean deleteFile(String path) {
        try {
            File file = new File(path);
            return !file.exists() || file.delete();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean fileExists(String path) {
        return new File(path).exists();
    }
}