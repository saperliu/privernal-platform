# ============================================================================
# 运行镜像。先在宿主机打包，再构建镜像：
#   mvn clean package
#   docker build -t privernal-server:latest -f privernal-server/Dockerfile privernal-server
#
# 本项目不在镜像内编译：Maven 依赖体量大，且离线/私服场景下把构建塞进镜像
# 会让 CI 难以复用本地仓库。
# ============================================================================

# ---------- 阶段一：拆分 Spring Boot 分层 jar ----------
# 编译目标是 Java 21（根 pom.xml 的 maven.compiler.target），
# 运行镜像的 JDK 不得低于它，否则启动即 UnsupportedClassVersionError。
FROM eclipse-temurin:21-jdk AS extractor
WORKDIR /build

ARG APP_NAME=privernal-server
COPY target/${APP_NAME}.jar app.jar

RUN java -Djarmode=layertools -jar app.jar extract

# ---------- 阶段二：运行镜像 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 按变化频率从低到高复制各层，最大化构建缓存命中
COPY --from=extractor /build/dependencies/ ./
COPY --from=extractor /build/spring-boot-loader/ ./
COPY --from=extractor /build/snapshot-dependencies/ ./
COPY --from=extractor /build/application/ ./

# 上传目录与日志目录用 volume 挂载，避免容器重建即丢数据
VOLUME ["/data/privernal/upload", "/data/privernal/logs"]

# 8600 HTTP，16001 Socket.IO 长连接
EXPOSE 8600 16001

# 配置全部来自环境变量，取值与默认项见 application-pro.yaml。
# 不要在镜像里删除 application*.yaml —— 那会连默认配置一起删掉，jar 将无法启动；
# 需要覆盖时设环境变量，或挂载外部 /app/config 目录（Spring Boot 自动优先读取）。
ENV SPRING_PROFILES_ACTIVE=pro \
    TZ=Asia/Shanghai \
    JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
