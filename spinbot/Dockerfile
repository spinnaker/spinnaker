FROM python:3

RUN useradd -ms /bin/bash spinbot

WORKDIR /usr/src/app

COPY . .

RUN chown -R spinbot *

RUN pip install --no-cache-dir -r requirements.txt

USER spinbot

CMD [ "./spinbot.py" ]
