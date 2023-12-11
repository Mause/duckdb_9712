FROM amazoncorretto:17.0.9

WORKDIR /tmp

COPY target/test-1.0-SNAPSHOT.jar app.jar

# JMX
EXPOSE 9003

# Run JAR with JMX arguments
CMD [ \
  "java", \
  "-Djava.rmi.server.hostname=0.0.0.0", \
  "-Dcom.sun.management.jmxremote", \
  "-Dcom.sun.management.jmxremote.port=9003", \
  "-Dcom.sun.management.jmxremote.rmi.port=9003", \
  "-Dcom.sun.management.jmxremote.ssl=false", \
  "-Dcom.sun.management.jmxremote.authenticate=false", \
  "-Djava.rmi.server.hostname=0.0.0.0", \
  "-jar", \
  "/tmp/app.jar" \
]
