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

CMD npm start
