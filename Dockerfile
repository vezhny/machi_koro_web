# syntax=docker/dockerfile:1

# ---- Build: Maven wrapper builds core, web (Node is downloaded by frontend-maven-plugin) and server.
# Tests are not run here; CI runs them (./mvnw verify).
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY core/ core/
COPY web/ web/
COPY server/ server/

# Caches survive between builds: Maven repository (incl. the Node archive) and npm cache.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm \
    sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q package -DskipTests -Dnpm.install=ci \
    && cp server/target/server-*.jar /src/app.jar

# ---- Runtime: JRE only, unprivileged user.
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
