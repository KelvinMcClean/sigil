# Build stage
FROM eclipse-temurin:25-jdk AS builder

# Install build dependencies
RUN apt-get update && apt-get install -y git curl && rm -rf /var/lib/apt/lists/*

# Install Node.js
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Add node.exe symlink for Windows-compatible npm packages
RUN ln -s $(which node) /usr/local/bin/node.exe

# Copy build files
WORKDIR /build
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build the frontend
RUN cd frontend && npm install && npm run build

# Build the backend, skipping frontend rebuild
RUN ./gradlew bootJar -x buildFrontendProduction

# Runtime stage
FROM eclipse-temurin:25

# Install runtime dependencies (ffmpeg)
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# Copy the built JAR and config from builder
COPY --from=builder /build/build/libs/sigil-*SNAPSHOT.jar app.jar
COPY --from=builder /build/application.yml application.yml

# Run the application
ENTRYPOINT ["java","-jar","/app.jar"]