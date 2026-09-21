package com.lion.travel.common;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * .env 文件加载器(启动时使用)。
 *
 * <p>Spring Boot 原生不读取 .env 文件,本类在 {@code main} 方法里、Spring 容器启动前
 * 读取 .env(KEY=VALUE 格式)并注入为系统属性,使 application.yml 中的 {@code ${VAR}}
 * 占位符可以解析。</p>
 *
 * <p>查找位置:</p>
 * <ul>
 *   <li>首选:<b>jar 同级目录</b>下的 {@code .env}(与在哪个目录执行命令无关)。
 *       Spring Boot fat jar 的 {@code CodeSource} 返回
 *       {@code jar:file:/.../app.jar!/BOOT-INF/classes!/} 这类嵌套 URI,
 *       需剥掉 {@code jar:} 前缀与 {@code !/...} 后缀才能取到真实 jar 路径;</li>
 *   <li>回退:jar 同级目录没有 .env、或开发态(IDE / {@code mvn spring-boot:run},尚无 jar)时,
 *       读取<b>当前工作目录</b>下的 {@code .env}。</li>
 * </ul>
 *
 * <p>优先级:真实环境变量 &gt; 系统属性(.env 注入) &gt; yml 默认值。
 * 已存在的环境变量不会被 .env 覆盖,方便生产环境用真实环境变量覆盖本地配置。</p>
 *
 * <p>注意:.env 仅用于本地开发/部署目录,且已被 .gitignore 忽略,不会提交到仓库,
 * 也不会被打进 jar(需与 jar 一起拷贝到同级目录)。</p>
 */
public final class EnvFileLoader {

    /** .env 文件名 */
    private static final String ENV_FILE_NAME = ".env";

    private EnvFileLoader() {
    }

    /**
     * 加载 .env:优先 jar 同级目录,该处不存在时回退当前工作目录。
     * 文件不存在或读取失败时静默跳过,不影响启动。
     */
    public static void load() {
        Path envFile = resolveJarDirEnv();
        if (!Files.isRegularFile(envFile)) {
            envFile = Path.of(ENV_FILE_NAME);
        }
        loadFile(envFile);
    }

    /**
     * 只加载指定路径的 .env 文件(保留单文件加载能力)。
     *
     * @param path .env 文件路径
     */
    public static void load(String path) {
        loadFile(Path.of(path));
    }

    /** jar 同级目录下的 .env;无法定位 jar 时返回当前工作目录下的 .env */
    private static Path resolveJarDirEnv() {
        Path jarDir = resolveJarDir();
        return jarDir != null ? jarDir.resolve(ENV_FILE_NAME) : Path.of(ENV_FILE_NAME);
    }

    /**
     * 解析运行包(jar)所在目录。
     *
     * <p>fat jar 的 CodeSource location 形如
     * {@code jar:file:/E:/app/app.jar!/BOOT-INF/classes!/},需先还原为
     * {@code file:/E:/app/app.jar} 再取父目录;开发态(class 位于 target/classes)
     * 或解析失败时返回 null,由调用方回退到工作目录。</p>
     */
    private static Path resolveJarDir() {
        try {
            var source = EnvFileLoader.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            String location = source.getLocation().toString();
            if (location.startsWith("jar:")) {
                location = location.substring("jar:".length());
            }
            int bang = location.indexOf("!/");
            if (bang > 0) {
                location = location.substring(0, bang);
            }
            Path path = Path.of(URI.create(location));
            return Files.isRegularFile(path) ? path.getParent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取单个 .env 文件并注入系统属性 */
    private static void loadFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Files.readAllLines(file).forEach(EnvFileLoader::applyLine);
            System.out.println("[EnvFileLoader] loaded: " + file.toAbsolutePath());
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
