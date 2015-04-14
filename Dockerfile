FROM dockerfile/nodejs

RUN apt-get update && apt-get install -y \
    curl libfreetype6 libfontconfig bzip2

ENV PHANTOMJS_VERSION 1.9.8

RUN curl -SLO "https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-$PHANTOMJS_VERSION-linux-x86_64.tar.bz2" \
    && tar -xjf "phantomjs-$PHANTOMJS_VERSION-linux-x86_64.tar.bz2" -C /usr/local --strip-components=1 \
    && rm "phantomjs-$PHANTOMJS_VERSION-linux-x86_64.tar.bz2"

COPY . deck/

WORKDIR deck

RUN npm update npm

RUN yes w | npm install -g gulp

ENV NODE_ENV dev gulp

RUN npm install 

RUN yes w | npm install -g bower

RUN bower install --allow-root 

RUN gulp build

CMD gulp