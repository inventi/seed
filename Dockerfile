FROM java:8

RUN mkdir -p /home/seed && \
      groupadd -r seed -g 433 && \
      useradd -u 431 -r -g seed -d /home/seed -s /sbin/nologin -c "Seed user" seed && \
      chown -R seed:seed /home/seed

ADD https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh /usr/local/bin/boot
RUN chmod 755 /usr/local/bin/boot

USER seed
RUN mkdir /home/seed/src /home/seed/test
ADD boot.properties /home/seed/
ADD build.boot /home/seed/
RUN cd /home/seed && boot javac
ADD src /home/seed/src
WORKDIR /home/seed
CMD ["boot","run"]
