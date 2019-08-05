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
COPY conf/tomcat/server.xml /usr/share/tomcat9/conf/
RUN mkdir /usr/share/tomcat9/temp
RUN echo "=== Done installing Tomcat."
#
#COPY naese_war/target/naese_war-0.0.1-SNAPSHOT.war /home/app/naese.war
#RUN /usr/share/tomcat9/bin/catalina.sh start
#RUN date && sleep 5 && echo "Done sleeping." && date
#RUN curl -u manager:FOOBAR -X PUT -T /home/app/naese.war http://localhost:8080/manager/text/deploy?path=/naese
#RUN /usr/share/tomcat9/bin/catalina.sh stop
###
# curl: (7) Failed to connect to localhost port 8080: Connection refused
# The command '/bin/sh -c curl -u manager:FOOBAR -X PUT -T /home/app/naese_war-0.0.1-SNAPSHOT.war http://localhost:8080/manager/text/deploy?path=/naese' returned a non-zero code: 7
#
###
# Another way to deploy:
COPY naese_war/target/naese_war-0.0.1-SNAPSHOT.war /usr/share/tomcat9/webapps/naese.war
RUN echo "=== Done installing application NAESE."
#
RUN apt-get install -y apache2
RUN apt-cache policy apache2
RUN a2enmod cgi proxy proxy_http
RUN echo "=== Done installing Apache."
#
COPY ./conf/apache/apache2.conf /etc/apache2/apache2.conf
COPY ./conf/apache/000-default.conf /etc/apache2/sites-available/000-default.conf
#COPY ./conf/serve-cgi-bin.conf /etc/apache2/conf-available/serve-cgi-bin.conf
RUN mkdir -p /home/www/htdocs
RUN mkdir -p /home/www/cgi-bin
RUN echo "=== Apache config copied."
RUN apachectl -D BACKGROUND
#
CMD ["/usr/share/tomcat9/bin/catalina.sh", "run"]
#
