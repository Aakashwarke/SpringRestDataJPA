# Two stages: the build layer keeps the JDK and the Maven cache out of the final image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copying the POM first means dependencies are only re-downloaded when it changes,
# not on every source edit.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run the application as root.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# MaxRAMPercentage lets the JVM size its heap from the container limit rather than
# from the host's total memory, which is what causes surprise OOM kills.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
