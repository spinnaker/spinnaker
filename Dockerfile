FROM node:7.0.0

COPY . deck/

WORKDIR deck

RUN npm install

CMD npm start
