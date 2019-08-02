#
FROM ubuntu:18.10
WORKDIR /home/app
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update
RUN apt-get install -y apt-utils
ENV TZ=America/Denver
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ >/etc/timezone
RUN apt-get install -y tzdata
RUN apt-get install -y curl
RUN echo "=== Done installing Ubuntu."
#
RUN apt-get install -y openjdk-8-jdk
RUN apt-cache policy openjdk-8-jdk
RUN java -version
RUN echo "=== Done installing Java."
#
RUN apt-get install -y software-properties-common
RUN apt-add-repository -y "deb http://us.archive.ubuntu.com/ubuntu/ bionic universe"
RUN apt-get install -y tomcat9 tomcat9-admin
RUN apt-cache policy tomcat9
# Why needed?
RUN cp -r /etc/tomcat9 /usr/share/tomcat9/conf
COPY conf/tomcat/tomcat-users.xml /usr/share/tomcat9/conf/
RUN echo "=== Done installing Tomcat."
#
COPY target/naese_war-0.0.1-SNAPSHOT.war /home/app/
RUN /usr/share/tomcat9/bin/catalina.sh start
RUN curl -u manager:FOOBAR -X PUT -T /home/app/naese_war-0.0.1-SNAPSHOT.war http://localhost:8080/manager/text/deploy?path=/naese
RUN /usr/share/tomcat9/bin/catalina.sh stop
RUN echo "=== Done installing application NAESE."
#
CMD ["/usr/share/tomcat9/bin/catalina.sh", "run"]
#
