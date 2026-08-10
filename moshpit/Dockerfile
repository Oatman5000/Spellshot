FROM amazoncorretto:25-headless
COPY server server
COPY docker.hyboot.conf server/hyboot.conf
WORKDIR /server
ENTRYPOINT ["java", "-jar", "server.jar"]