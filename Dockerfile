# =====================================================================
# lion-mcp-travel-planner 运行镜像(单阶段:直接使用本地已打好的 fat jar)
#
# 前置:先在宿主机打包(二选一)
#   mvn clean package -DskipTests
#   bin\package-backend.bat
#
# 构建(项目根目录): docker build -t lion-mcp-travel-planner:1.0.0 .
#   指定其它 jar 名: docker build --build-arg JAR_FILE=xxx.jar -t lion-mcp-travel-planner:1.0.0 .
# 运行: docker run -d -p 8083:8083 --env-file .env lion-mcp-travel-planner:1.0.0
# =====================================================================

FROM eclipse-temurin:21-jre-jammy

# 时区 / 字符集:与 MySQL serverTimezone=Asia/Shanghai、utf8mb4 对齐
# JVM 堆为保守默认值,生产请在 compose / docker run 中按机器内存覆盖 JAVA_OPTS
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    SERVER_PORT=8083 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

# curl 用于 HEALTHCHECK;tzdata 保证 JVM 时区正确
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata curl \
    && ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime && echo ${TZ} > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# jar 名称需与 pom.xml 的 artifactId/version 一致(默认 lion-mcp-travel-planner-1.0.0.jar)
ARG JAR_FILE=lion-mcp-travel-planner-1.0.0.jar
COPY target/${JAR_FILE} /app/app.jar

# 非 root 运行(uid 10001)
RUN useradd -m -u 10001 appuser \
    && touch /tmp/.keep && chown -R appuser:appuser /app /tmp
USER appuser

EXPOSE ${SERVER_PORT}

# 健康探针:走 /api/health(DB/Redis 未就绪时非 healthy),compose 依赖它做启动编排
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/api/health" || exit 1

# 敏感配置一律由环境变量注入,镜像内不含任何密钥
# EnvFileLoader 会读取 jar 同级目录的 .env(可选挂载),真实环境变量优先级更高
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
