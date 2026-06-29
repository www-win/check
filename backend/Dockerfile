# ===== 构建阶段：Maven 打包 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY settings-docker.xml .
COPY pom.xml .
# 先拉依赖（利用缓存层，源码变更时不必重新下载依赖）
RUN mvn -B -s settings-docker.xml dependency:go-offline
COPY src ./src
RUN mvn -B -s settings-docker.xml -DskipTests clean package

# ===== 运行阶段：仅 JRE，镜像更小 =====
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/studybuddy-backend-0.1.0.jar app.jar
# 内存可按云托管实例规格调整（0.5G 实例建议 -Xmx256m，1G 实例可 -Xmx512m）
ENV JAVA_OPTS="-Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
