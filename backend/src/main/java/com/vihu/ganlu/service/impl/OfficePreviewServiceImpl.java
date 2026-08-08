package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.service.OfficePreviewService;
import com.vihu.ganlu.utils.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OfficePreviewServiceImpl implements OfficePreviewService {
    private final FileStorageUtil fileStorageUtil;
    private final String configuredExecutable;
    private final long timeoutSeconds;

    public OfficePreviewServiceImpl(
            FileStorageUtil fileStorageUtil,
            @Value("${material.libreoffice.executable:}") String configuredExecutable,
            @Value("${material.libreoffice.timeout-seconds:90}") long timeoutSeconds) {
        this.fileStorageUtil = fileStorageUtil;
        this.configuredExecutable = configuredExecutable;
        this.timeoutSeconds = Math.max(10L, timeoutSeconds);
    }

    @Override
    public Path convertToPdf(Path sourceFile, Path targetFile) throws IOException {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new IOException("待转换的演示文稿不存在");
        }
        Files.createDirectories(targetFile.getParent());
        Path workRoot = fileStorageUtil.createDirectory("office-work");
        Path workDir = workRoot.resolve(UUID.randomUUID().toString()).normalize();
        Files.createDirectories(workDir);

        Process process = null;
        ExecutorService outputExecutor = Executors.newSingleThreadExecutor();
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    resolveExecutable(),
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nofirststartwizard",
                    "--convert-to", "pdf",
                    "--outdir", workDir.toString(),
                    sourceFile.toAbsolutePath().toString()
            );
            builder.redirectErrorStream(true);
            process = builder.start();
            final Process runningProcess = process;
            Future<String> output = outputExecutor.submit(() -> readOutput(runningProcess));

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("PPT 预览转换超时");
            }
            String commandOutput;
            try {
                commandOutput = output.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                commandOutput = "无法读取 LibreOffice 输出";
            }
            if (process.exitValue() != 0) {
                log.warn("LibreOffice 转换失败，exit={}, output={}", process.exitValue(), commandOutput);
                throw new IOException("PPT 预览转换失败");
            }

            String filename = sourceFile.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String outputName = (dot > 0 ? filename.substring(0, dot) : filename) + ".pdf";
            Path converted = workDir.resolve(outputName);
            if (!Files.isRegularFile(converted)) {
                log.warn("LibreOffice 未生成预期文件，output={}", commandOutput);
                throw new IOException("PPT 预览文件未生成");
            }
            Files.move(converted, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PPT 预览转换被中断", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            outputExecutor.shutdownNow();
            try {
                fileStorageUtil.deleteTree(workDir);
            } catch (RuntimeException cleanupError) {
                log.warn("清理 LibreOffice 临时目录失败: {}", cleanupError.getMessage());
            }
        }
    }

    private String resolveExecutable() throws IOException {
        if (StringUtils.hasText(configuredExecutable)) {
            Path configured = Paths.get(configuredExecutable).toAbsolutePath().normalize();
            if (!Files.isRegularFile(configured)) {
                throw new IOException("LibreOffice 可执行文件不存在: " + configured);
            }
            return configured.toString();
        }
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            Path defaultPath = Paths.get("C:\\Program Files\\LibreOffice\\program\\soffice.exe");
            if (!Files.isRegularFile(defaultPath)) {
                throw new IOException("未找到 LibreOffice，请配置 material.libreoffice.executable");
            }
            return defaultPath.toString();
        }
        return "soffice";
    }

    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 8000) {
                    output.append(line).append('\n');
                }
            }
        }
        return output.toString();
    }
}
