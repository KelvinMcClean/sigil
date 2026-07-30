FROM eclipse-temurin:25
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*
COPY "build/libs/sigil-*SNAPSHOT.jar" app.jar
COPY "application.yml" application.yml
ENTRYPOINT ["java","-jar","/app.jar"]