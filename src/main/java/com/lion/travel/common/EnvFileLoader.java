package com.lion.travel.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * .env 文件加载器(启动时使用)。
 *
 * <p>Spring Boot 原生不读取 .env 文件,本类在 {@code main} 方法里、Spring 容器启动前
 * 读取项目根目录的 .env(KEY=VALUE 格式)并注入为系统属性,使
 * application.yml 中的 {@code ${VAR}} 占位符可以解析。</p>
 *
 * <p>优先级:真实环境变量 &gt; 系统属性(.env 注入) &gt; yml 默认值。
 * 已存在的环境变量不会被 .env 覆盖,方便生产环境用真实环境变量覆盖本地配置。</p>
 *
 * <p>注意:.env 仅用于本地开发,且已被 .gitignore 忽略,不会提交到仓库。</p>
 */
public final class EnvFileLoader {

    /** 默认 .env 文件路径(相对项目根目录,即启动时的工作目录) */
    private static final String DEFAULT_ENV_PATH = ".env";

    private EnvFileLoader() {
    }

    /**
     * 加载项目根目录下的 .env 文件。
     * 文件不存在或读取失败时静默跳过,不影响启动。
     */
    public static void load() {
        load(DEFAULT_ENV_PATH);
    }

    /**
     * 加载指定路径的 .env 文件。
     *
     * @param path .env 文件路径
     */
    public static void load(String path) {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            return;
        }
        try {
            Files.readAllLines(file).forEach(EnvFileLoader::applyLine);
        } catch (IOException e) {
            System.err.println("[EnvFileLoader] 读取 .env 失败:" + e.getMessage());
        }
    }

    /** 解析单行 KEY=VALUE 并注入系统属性 */
    private static void applyLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        int idx = line.indexOf('=');
        if (idx <= 0) {
            return;
        }
        String key = line.substring(0, idx).trim();
        String value = line.substring(idx + 1).trim();
        // 移除可能的引号("value" / 'value')
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        // 真实环境变量优先,避免覆盖操作系统的同名变量
        if (System.getenv(key) != null) {
            return;
        }
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
