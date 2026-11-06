FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY settings.xml pom.xml ./
COPY lib ./lib
RUN --mount=type=cache,target=/root/.m2 mvn -B -s settings.xml dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -s settings.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN for package in fontconfig freetype; do \
        apk info -e "$package" >/dev/null || apk add --no-cache "$package"; \
    done
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
