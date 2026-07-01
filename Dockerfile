# Multi-stage build for smali-skills.
# Stage 1 builds the fat jars with Gradle on a JDK.
# Stage 2 copies just the fat jars into a minimal JRE image for runtime.

# ---------- build stage ----------
# Use a Gradle 8.14 image matching the project's wrapper version. We invoke the
# image's gradle directly to avoid downloading the wrapper distribution inside
# the image.
FROM gradle:8.14-jdk17 AS builder

WORKDIR /build

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Copy the build scripts first so dependency resolution is cached across
# source-only changes.
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY util/build.gradle ./util/build.gradle
COPY dexlib2/build.gradle ./dexlib2/build.gradle
COPY dexlib2/accessorTestGenerator/build.gradle ./dexlib2/accessorTestGenerator/build.gradle
COPY smali/build.gradle ./smali/build.gradle
COPY baksmali/build.gradle ./baksmali/build.gradle

# Pre-resolve dependencies (best-effort; tolerates failure if offline).
RUN gradle --no-daemon :smali:dependencies :baksmali:dependencies || true

# Copy sources.
COPY util ./util
COPY dexlib2 ./dexlib2
COPY smali ./smali
COPY baksmali ./baksmali

# Build fat jars. The fatJar tasks now declare explicit dependsOn for the
# upstream util/dexlib2 jars, so a single invocation is enough.
RUN gradle --no-daemon :smali:fatJar :baksmali:fatJar -x test -x javadoc -x check

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre-jammy

WORKDIR /work

# Copy the fat jars. The fat jar already contains every dependency, so the
# runtime image needs no further classpath.
COPY --from=builder /build/smali/build/libs/*-fat.jar /opt/smali-skills/smali.jar
COPY --from=builder /build/baksmali/build/libs/*-fat.jar /opt/smali-skills/baksmali.jar

# Convenience wrappers so users can run `smali` / `baksmali` directly.
RUN printf '#!/bin/sh\nexec java -jar /opt/smali-skills/smali.jar "$@"\n' > /usr/local/bin/smali && \
    printf '#!/bin/sh\nexec java -jar /opt/smali-skills/baksmali.jar "$@"\n' > /usr/local/bin/baksmali && \
    chmod +x /usr/local/bin/smali /usr/local/bin/baksmali

# Default to baksmali; override with e.g. `docker run <img> smali assemble ...`.
ENTRYPOINT ["baksmali"]
CMD ["--help"]
