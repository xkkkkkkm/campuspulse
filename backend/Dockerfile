FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -f backend/pom.xml dependency:go-offline -DskipTests
COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 10001 campus && useradd -u 10001 -g campus -M campus \
    && mkdir /app/uploads && chown campus:campus /app/uploads
COPY --from=build /workspace/backend/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod APP_UPLOAD_ROOT=/app/uploads TZ=Asia/Shanghai JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai"
USER campus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
