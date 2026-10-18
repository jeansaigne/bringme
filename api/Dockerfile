# syntax=docker/dockerfile:1
# Build : image Gradle officielle (pas de wrapper versionné), tests exécutés en CI et non ici.
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY config config
RUN gradle --no-daemon -q dependencies > /dev/null
COPY src src
RUN gradle --no-daemon -q bootJar -x test && cp build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app.jar /app.jar
# Free tier Render : 512 Mo. Heap bornée, GC série, pile réduite.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
