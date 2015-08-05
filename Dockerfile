FROM node

COPY . deck/

RUN useradd -ms /bin/bash node

RUN chown -R node deck

RUN chown -R node /usr/

ENV HOME /home/node

USER node

WORKDIR deck

RUN rm -rf .git

RUN npm install

CMD node ./node_modules/webpack-dev-server/bin/webpack-dev-server.js --host 0.0.0.0 --port 9000