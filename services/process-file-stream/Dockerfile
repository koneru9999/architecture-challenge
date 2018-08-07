FROM alpine:edge
MAINTAINER Venkaiah Chowdary Koneru <koneru.chowdary@gmail.com>
RUN apk add --no-cache openjdk8
COPY target/process-file-stream.jar /opt/lib/
ENTRYPOINT ["/usr/bin/java"]
CMD ["-jar", "/opt/lib/process-file-stream.jar"]
EXPOSE 8080