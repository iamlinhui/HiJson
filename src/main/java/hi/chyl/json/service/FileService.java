package hi.chyl.json.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 文件 IO 服务
 * 提供文件读取和保存功能
 */
public class FileService {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 文件操作结果
     */
    public static class FileResult {
        private final boolean success;
        private final String content;
        private final String errorMessage;

        private FileResult(boolean success, String content, String errorMessage) {
            this.success = success;
            this.content = content;
            this.errorMessage = errorMessage;
        }

        public static FileResult success(String content) {
            return new FileResult(true, content, null);
        }

        public static FileResult failure(String errorMessage) {
            return new FileResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 读取文件内容
     *
     * @param file 文件对象
     * @return 文件操作结果
     */
    public FileResult readFile(File file) {
        return readFile(file, DEFAULT_CHARSET);
    }

    /**
     * 读取文件内容（指定编码）
     *
     * @param file    文件对象
     * @param charset 字符编码
     * @return 文件操作结果
     */
    public FileResult readFile(File file, Charset charset) {
        if (file == null || !file.exists()) {
            return FileResult.failure("文件不存在");
        }

        if (!file.canRead()) {
            return FileResult.failure("文件无法读取");
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String content = new String(bytes, charset);
            return FileResult.success(content);
        } catch (IOException e) {
            return FileResult.failure("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存内容到文件
     *
     * @param file    文件对象
     * @param content 要保存的内容
     * @return 文件操作结果
     */
    public FileResult saveFile(File file, String content) {
        return saveFile(file, content, DEFAULT_CHARSET);
    }

    /**
     * 保存内容到文件（指定编码）
     *
     * @param file    文件对象
     * @param content 要保存的内容
     * @param charset 字符编码
     * @return 文件操作结果
     */
    public FileResult saveFile(File file, String content, Charset charset) {
        if (file == null) {
            return FileResult.failure("文件路径无效");
        }

        if (content == null) {
            content = "";
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), charset)) {
            // 统一换行符为 Windows 格式
            String normalizedContent = content.replace("\n", "\r\n");
            writer.write(normalizedContent);
            return FileResult.success(null);
        } catch (IOException e) {
            return FileResult.failure("保存文件失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean exists(File file) {
        return file != null && file.exists();
    }

    /**
     * 检查文件是否可读
     */
    public boolean canRead(File file) {
        return file != null && file.exists() && file.canRead();
    }

    /**
     * 检查文件是否可写
     */
    public boolean canWrite(File file) {
        if (file == null) {
            return false;
        }
        if (file.exists()) {
            return file.canWrite();
        }
        // 如果文件不存在，检查父目录是否可写
        File parent = file.getParentFile();
        return parent != null && parent.exists() && parent.canWrite();
    }
}
