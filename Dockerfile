FROM gradle:8.5-jdk17-alpine AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.source=https://github.com/thvhdvy/NguyenHuuThanh_JB-IOC-260310_DevOps_SS08_BaiTapTongHop
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]